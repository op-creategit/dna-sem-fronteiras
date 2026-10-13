"""Servidor do Controle Xbox Wi-Fi.

Recebe o estado do controle enviado pelo app do celular (UDP) e repassa para
um controle Xbox 360 virtual criado pelo driver ViGEmBus. Até 4 celulares ao
mesmo tempo, cada um vira um jogador. Protocolo descrito em PROTOCOLO.md.
"""

import argparse
import glob
import os
import socket
import struct
import subprocess
import sys
import threading
import time

VERSAO = "1.0.0"
PORTA_PADRAO = 7777
MAGICO = b"X3WF"

T_ESTADO = 1
T_PROCURAR = 2
T_RESPOSTA = 3
T_TCHAU = 4
T_VIBRAR = 5
T_CONFIRMA = 6

ESTADO = struct.Struct("<4sBBHHBBhhhh")
MAX_JOGADORES = 4
TEMPO_LIMITE = 3.0
INTERVALO_CONFIRMA = 1.0


class ControleFalso:
    """Controle de mentira, para testar sem o driver instalado."""

    def __init__(self, ao_vibrar):
        self.ao_vibrar = ao_vibrar
        self.estado = (0, 0, 0, 0, 0, 0, 0)
        self.atualizacoes = 0

    def aplicar(self, botoes, lt, rt, lx, ly, rx, ry):
        self.estado = (botoes, lt, rt, lx, ly, rx, ry)
        self.atualizacoes += 1

    def fechar(self):
        pass


class ControleVirtual:
    """Controle Xbox 360 virtual via vgamepad (ViGEmBus no Windows)."""

    def __init__(self, ao_vibrar):
        import vgamepad

        self.pad = vgamepad.VX360Gamepad()

        # A assinatura precisa ser exatamente esta, o vgamepad confere os nomes.
        def notificacao(client, target, large_motor, small_motor, led_number, user_data):
            ao_vibrar(large_motor, small_motor)

        self._notificacao = notificacao
        try:
            self.pad.register_notification(notificacao)
        except Exception:
            pass  # sem vibração, o resto funciona

    def aplicar(self, botoes, lt, rt, lx, ly, rx, ry):
        self.pad.report.wButtons = botoes
        self.pad.left_trigger(lt)
        self.pad.right_trigger(rt)
        self.pad.left_joystick(lx, ly)
        self.pad.right_joystick(rx, ry)
        self.pad.update()

    def fechar(self):
        try:
            self.pad.unregister_notification()
        except Exception:
            pass
        del self.pad


class Jogador:
    def __init__(self, numero, endereco, controle):
        self.numero = numero
        self.endereco = endereco
        self.controle = controle
        self.sequencia = None
        self.visto = time.monotonic()
        self.confirmado = 0.0


def sequencia_nova(antes, agora):
    """Aceita o pacote se ele vem depois do último (contando a volta em 65535)."""
    if antes is None:
        return True
    diferenca = (agora - antes) & 0xFFFF
    return 0 < diferenca < 0x8000


class Servidor:
    def __init__(self, porta=PORTA_PADRAO, fabrica=None, log=print, host="0.0.0.0"):
        self.fabrica = fabrica or ControleVirtual
        self.log = log
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.sock.bind((host, porta))
        self.sock.settimeout(0.5)
        self.porta = self.sock.getsockname()[1]
        self.jogadores = {}
        self.cheios_avisados = set()
        self.nome = socket.gethostname() or "PC"
        self._parar = threading.Event()

    def parar(self):
        self._parar.set()

    def rodar(self):
        while not self._parar.is_set():
            try:
                dados, endereco = self.sock.recvfrom(64)
            except socket.timeout:
                dados = None
            except OSError:
                # No Windows, um ICMP "porta inacessível" aparece aqui como erro.
                dados = None
            if dados:
                self.tratar(dados, endereco)
            self.limpar_inativos()
        for jogador in list(self.jogadores.values()):
            self.remover(jogador, "servidor encerrado")
        self.sock.close()

    def enviar(self, endereco, tipo, corpo=b""):
        try:
            self.sock.sendto(MAGICO + bytes([tipo]) + corpo, endereco)
        except OSError:
            pass

    def tratar(self, dados, endereco):
        if len(dados) < 5 or dados[:4] != MAGICO:
            return
        tipo = dados[4]
        if tipo == T_PROCURAR:
            self.enviar(endereco, T_RESPOSTA, self.nome.encode("utf-8")[:48])
        elif tipo == T_TCHAU:
            jogador = self.jogadores.get(endereco)
            if jogador:
                self.remover(jogador, "o celular saiu")
        elif tipo == T_ESTADO and len(dados) >= ESTADO.size:
            self.tratar_estado(dados, endereco)

    def tratar_estado(self, dados, endereco):
        _, _, _, seq, botoes, lt, rt, lx, ly, rx, ry = ESTADO.unpack_from(dados)
        jogador = self.jogadores.get(endereco) or self.adicionar(endereco)
        if jogador is None:
            return
        agora = time.monotonic()
        jogador.visto = agora
        if agora - jogador.confirmado >= INTERVALO_CONFIRMA:
            jogador.confirmado = agora
            self.enviar(endereco, T_CONFIRMA, bytes([jogador.numero]))
        if not sequencia_nova(jogador.sequencia, seq):
            return
        jogador.sequencia = seq
        try:
            jogador.controle.aplicar(botoes, lt, rt, lx, ly, rx, ry)
        except Exception as erro:
            self.log(f"Erro ao atualizar o jogador {jogador.numero}: {erro}")

    def adicionar(self, endereco):
        livres = set(range(1, MAX_JOGADORES + 1)) - {j.numero for j in self.jogadores.values()}
        if not livres:
            self.enviar(endereco, T_CONFIRMA, bytes([0]))
            if endereco not in self.cheios_avisados:
                self.cheios_avisados.add(endereco)
                self.log(f"Recusado {endereco[0]}: já há {MAX_JOGADORES} jogadores.")
            return None
        numero = min(livres)

        def ao_vibrar(grande, pequeno, endereco=endereco):
            self.enviar(endereco, T_VIBRAR, bytes([grande & 0xFF, pequeno & 0xFF]))

        try:
            controle = self.fabrica(ao_vibrar)
        except Exception as erro:
            self.log(f"Não consegui criar o controle virtual: {erro}")
            self.enviar(endereco, T_CONFIRMA, bytes([0]))
            return None
        jogador = Jogador(numero, endereco, controle)
        self.jogadores[endereco] = jogador
        self.cheios_avisados.discard(endereco)
        self.log(f"Jogador {numero} conectado ({endereco[0]}).")
        return jogador

    def remover(self, jogador, motivo):
        self.jogadores.pop(jogador.endereco, None)
        try:
            jogador.controle.fechar()
        except Exception:
            pass
        self.log(f"Jogador {jogador.numero} desconectado ({motivo}).")

    def limpar_inativos(self):
        agora = time.monotonic()
        for jogador in list(self.jogadores.values()):
            if agora - jogador.visto > TEMPO_LIMITE:
                self.remover(jogador, "sem sinal")


def enderecos_locais():
    ips = set()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))  # não envia nada, só escolhe a interface
        ips.add(s.getsockname()[0])
        s.close()
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ips.add(info[4][0])
    except OSError:
        pass
    return sorted(ip for ip in ips if not ip.startswith("127."))


def pasta_do_vgamepad():
    # No .exe os arquivos do vgamepad (DLL e instalador do driver) ficam em _MEIPASS.
    empacotado = getattr(sys, "_MEIPASS", None)
    if empacotado:
        return os.path.join(empacotado, "vgamepad")
    try:
        import importlib.util

        spec = importlib.util.find_spec("vgamepad")
        if spec and spec.origin:
            return os.path.dirname(spec.origin)
    except Exception:
        pass
    return None


def instalador_do_driver():
    pasta = pasta_do_vgamepad()
    arq = "x64" if sys.maxsize > 2**32 else "x86"
    msis = glob.glob(os.path.join(pasta, "win", "vigem", "install", arq, "*.msi")) if pasta else []
    return msis[0] if msis else None


def verificar_pacote():
    """Usado no build: confere se a DLL do ViGEm e o instalador do driver vieram junto."""
    pasta = pasta_do_vgamepad()
    dlls = glob.glob(os.path.join(pasta, "win", "vigem", "client", "*", "ViGEmClient.dll")) if pasta else []
    msi = instalador_do_driver()
    print(f"vgamepad: {pasta}")
    print(f"DLLs: {dlls}")
    print(f"instalador: {msi}")
    return 0 if dlls and msi else 1


def oferecer_driver():
    """Sem ViGEmBus no Windows, oferece instalar o driver que vem junto."""
    print()
    print("O driver ViGEmBus, que cria o controle Xbox 360 virtual, não foi encontrado.")
    msi = instalador_do_driver()
    if sys.platform == "win32" and msi:
        resposta = input("Instalar o driver agora? (s/n) ").strip().lower()
        if resposta.startswith("s"):
            subprocess.call(["msiexec", "/i", msi])
            print("Depois de instalar, feche e abra este programa de novo.")
            return
    print("Baixe e instale em: https://github.com/nefarius/ViGEmBus/releases")
    print("Depois feche e abra este programa de novo.")


def testar_driver():
    try:
        controle = ControleVirtual(lambda g, p: None)
        controle.fechar()
        return True
    except Exception as erro:
        print(f"Falha ao criar o controle virtual: {erro}")
        return False


def principal():
    parser = argparse.ArgumentParser(description="Servidor do Controle Xbox Wi-Fi")
    parser.add_argument("--porta", type=int, default=PORTA_PADRAO, help="porta UDP (padrão 7777)")
    parser.add_argument("--teste", action="store_true", help="não cria controle de verdade, só mostra o que chega")
    parser.add_argument("--verificar-pacote", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if args.verificar_pacote:
        return verificar_pacote()

    print(f"Controle Xbox Wi-Fi, servidor {VERSAO}")
    fabrica = ControleVirtual
    if args.teste:
        def fabrica(ao_vibrar):
            return ControleImpresso(ao_vibrar)
    elif not testar_driver():
        oferecer_driver()
        input("Aperte Enter para sair.")
        return 1

    try:
        servidor = Servidor(args.porta, fabrica)
    except OSError as erro:
        print(f"Não consegui abrir a porta {args.porta}: {erro}")
        print("Talvez o servidor já esteja aberto em outra janela.")
        input("Aperte Enter para sair.")
        return 1

    print()
    print(f"Pronto. Esperando celulares na porta UDP {servidor.porta}.")
    ips = enderecos_locais()
    if ips:
        print("Se o app não achar o PC sozinho, digite um destes IPs nele:")
        for ip in ips:
            print(f"    {ip}")
    print("Se o Windows perguntar sobre o firewall, permita em redes privadas.")
    print("Para sair, feche esta janela ou aperte Ctrl+C.")
    print()
    try:
        servidor.rodar()
    except KeyboardInterrupt:
        servidor.parar()
    return 0


class ControleImpresso(ControleFalso):
    def aplicar(self, botoes, lt, rt, lx, ly, rx, ry):
        if (botoes, lt, rt) != self.estado[:3]:
            print(f"  botões={botoes:04x} LT={lt} RT={rt} LX={lx} LY={ly} RX={rx} RY={ry}")
        super().aplicar(botoes, lt, rt, lx, ly, rx, ry)


if __name__ == "__main__":
    sys.exit(principal())
