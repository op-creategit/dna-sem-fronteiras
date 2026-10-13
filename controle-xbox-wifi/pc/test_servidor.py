import socket
import struct
import threading
import time
import unittest

import servidor as srv


class TestServidor(unittest.TestCase):
    def setUp(self):
        self.criados = []

        def fabrica(ao_vibrar):
            controle = srv.ControleFalso(ao_vibrar)
            self.criados.append(controle)
            return controle

        self.servidor = srv.Servidor(0, fabrica, log=lambda *_: None, host="127.0.0.1")
        self.thread = threading.Thread(target=self.servidor.rodar, daemon=True)
        self.thread.start()
        self.destino = ("127.0.0.1", self.servidor.porta)
        self.celulares = []

    def tearDown(self):
        self.servidor.parar()
        self.thread.join(2)
        for c in self.celulares:
            c.close()

    def celular(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(1)
        s.bind(("127.0.0.1", 0))
        self.celulares.append(s)
        return s

    def estado(self, seq, botoes=0, lt=0, rt=0, lx=0, ly=0, rx=0, ry=0):
        return srv.ESTADO.pack(srv.MAGICO, srv.T_ESTADO, 0, seq, botoes, lt, rt, lx, ly, rx, ry)

    def esperar(self, condicao):
        limite = time.time() + 2
        while time.time() < limite:
            if condicao():
                return True
            time.sleep(0.01)
        return False

    def test_tamanho_do_pacote(self):
        self.assertEqual(srv.ESTADO.size, 20)

    def test_procura_responde_nome(self):
        c = self.celular()
        c.sendto(srv.MAGICO + bytes([srv.T_PROCURAR]), self.destino)
        dados, _ = c.recvfrom(64)
        self.assertEqual(dados[:5], srv.MAGICO + bytes([srv.T_RESPOSTA]))

    def test_estado_chega_ao_controle_e_confirma(self):
        c = self.celular()
        c.sendto(self.estado(1, 0x1001, 255, 10, -32768, 32767, 5, -5), self.destino)
        dados, _ = c.recvfrom(64)
        self.assertEqual(dados, srv.MAGICO + bytes([srv.T_CONFIRMA, 1]))
        self.assertTrue(self.esperar(lambda: self.criados and self.criados[0].atualizacoes == 1))
        self.assertEqual(self.criados[0].estado, (0x1001, 255, 10, -32768, 32767, 5, -5))

    def test_descarta_pacote_atrasado(self):
        c = self.celular()
        c.sendto(self.estado(10, 0x1000), self.destino)
        c.recvfrom(64)
        c.sendto(self.estado(9, 0x2000), self.destino)
        c.sendto(self.estado(11, 0x4000), self.destino)
        self.assertTrue(self.esperar(lambda: self.criados[0].atualizacoes == 2))
        self.assertEqual(self.criados[0].estado[0], 0x4000)

    def test_sequencia_da_volta(self):
        self.assertTrue(srv.sequencia_nova(65535, 0))
        self.assertFalse(srv.sequencia_nova(0, 65535))
        self.assertFalse(srv.sequencia_nova(5, 5))

    def test_quatro_jogadores_e_o_quinto_recusado(self):
        numeros = []
        for i in range(5):
            c = self.celular()
            c.sendto(self.estado(1), self.destino)
            dados, _ = c.recvfrom(64)
            numeros.append(dados[5])
        self.assertEqual(numeros, [1, 2, 3, 4, 0])

    def test_tchau_libera_a_vaga(self):
        a = self.celular()
        a.sendto(self.estado(1), self.destino)
        a.recvfrom(64)
        a.sendto(srv.MAGICO + bytes([srv.T_TCHAU]), self.destino)
        self.assertTrue(self.esperar(lambda: not self.servidor.jogadores))
        b = self.celular()
        b.sendto(self.estado(1), self.destino)
        dados, _ = b.recvfrom(64)
        self.assertEqual(dados[5], 1)

    def test_vibracao_volta_para_o_celular(self):
        c = self.celular()
        c.sendto(self.estado(1), self.destino)
        c.recvfrom(64)
        self.criados[0].ao_vibrar(200, 50)
        dados, _ = c.recvfrom(64)
        self.assertEqual(dados, srv.MAGICO + bytes([srv.T_VIBRAR, 200, 50]))

    def test_ignora_lixo(self):
        c = self.celular()
        c.sendto(b"lixo", self.destino)
        c.sendto(srv.MAGICO + bytes([srv.T_ESTADO, 1, 2]), self.destino)
        time.sleep(0.1)
        self.assertEqual(self.servidor.jogadores, {})


if __name__ == "__main__":
    unittest.main()
