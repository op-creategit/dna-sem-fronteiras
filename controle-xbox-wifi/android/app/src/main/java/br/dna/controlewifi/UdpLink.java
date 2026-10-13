package br.dna.controlewifi;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conversa com o servidor do PC por UDP. Protocolo em PROTOCOLO.md.
 * Uma thread manda o estado a cada 10 ms (ou a procura, sem PC escolhido);
 * outra recebe respostas, confirmações e vibração.
 */
public class UdpLink {

    public static final int PORTA = 7777;

    static final byte[] MAGICO = {'X', '3', 'W', 'F'};
    static final int T_ESTADO = 1;
    static final int T_PROCURAR = 2;
    static final int T_RESPOSTA = 3;
    static final int T_TCHAU = 4;
    static final int T_VIBRAR = 5;
    static final int T_CONFIRMA = 6;

    private static final long INTERVALO_ESTADO_MS = 10;
    private static final long INTERVALO_PROCURA_MS = 1000;
    private static final long CONFIRMA_EXPIRA_MS = 2000;

    public interface Ouvinte {
        void aoEncontrarPc(String nome, String ip);

        void aoVibrar(int motorGrande, int motorPequeno);
    }

    private final ControllerState estado;
    private final Ouvinte ouvinte;

    private volatile DatagramSocket socket;
    private volatile InetSocketAddress destino;
    private volatile boolean procurando;
    private volatile int jogador = -1;
    private volatile long ultimaConfirmacao;
    private volatile String nomePc;

    public UdpLink(ControllerState estado, Ouvinte ouvinte) {
        this.estado = estado;
        this.ouvinte = ouvinte;
    }

    /** Usa um IP (ou nome) digitado. */
    public void conectar(final String ip) {
        mandarTchau();
        procurando = false;
        destino = null;
        nomePc = ip;
        jogador = -1;
        ultimaConfirmacao = 0;
        // Resolver um nome pode usar a rede, então fica fora da thread principal.
        new Thread(new Runnable() {
            @Override
            public void run() {
                InetSocketAddress d = new InetSocketAddress(ip, PORTA);
                if (!d.isUnresolved() && ip.equals(nomePc) && !procurando) destino = d;
            }
        }).start();
    }

    /** Procura o PC por broadcast e conecta no primeiro que responder. */
    public void procurar() {
        mandarTchau();
        destino = null;
        nomePc = null;
        jogador = -1;
        ultimaConfirmacao = 0;
        procurando = true;
    }

    public boolean estaProcurando() {
        return procurando;
    }

    public String getNomePc() {
        return nomePc;
    }

    /** Número do jogador confirmado pelo PC, 0 se o PC está cheio, -1 se sem resposta. */
    public int getJogador() {
        if (System.currentTimeMillis() - ultimaConfirmacao > CONFIRMA_EXPIRA_MS) return -1;
        return jogador;
    }

    public synchronized void iniciar() {
        if (socket != null) return;
        final DatagramSocket s;
        try {
            s = new DatagramSocket();
            s.setBroadcast(true);
            s.setSoTimeout(500);
        } catch (Exception e) {
            return;
        }
        socket = s;
        // Cada par de threads fica preso ao seu socket e para quando ele é trocado.
        new Thread(new Runnable() {
            @Override
            public void run() {
                lacoEnvio(s);
            }
        }, "envio").start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                lacoRecepcao(s);
            }
        }, "recepcao").start();
    }

    public synchronized void parar() {
        final DatagramSocket s = socket;
        if (s == null) return;
        socket = null;
        final InetSocketAddress d = destino;
        // A thread principal não pode usar a rede.
        new Thread(new Runnable() {
            @Override
            public void run() {
                enviar(s, d, cabecalho(T_TCHAU));
                s.close();
            }
        }).start();
    }

    private void mandarTchau() {
        final DatagramSocket s = socket;
        final InetSocketAddress d = destino;
        if (s == null || d == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                enviar(s, d, cabecalho(T_TCHAU));
            }
        }).start();
    }

    private void lacoEnvio(DatagramSocket s) {
        int seq = 0;
        long ultimaProcura = 0;
        byte[] buf = new byte[20];
        while (socket == s) {
            InetSocketAddress d = destino;
            if (d != null) {
                seq = (seq + 1) & 0xFFFF;
                montarEstado(buf, seq);
                enviar(s, d, buf);
            } else if (procurando) {
                long agora = System.currentTimeMillis();
                if (agora - ultimaProcura >= INTERVALO_PROCURA_MS) {
                    ultimaProcura = agora;
                    byte[] pacote = cabecalho(T_PROCURAR);
                    for (InetAddress b : enderecosDeBroadcast()) {
                        enviar(s, new InetSocketAddress(b, PORTA), pacote);
                    }
                }
            }
            try {
                Thread.sleep(INTERVALO_ESTADO_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void lacoRecepcao(DatagramSocket s) {
        byte[] buf = new byte[128];
        DatagramPacket pacote = new DatagramPacket(buf, buf.length);
        while (socket == s) {
            try {
                pacote.setLength(buf.length);
                s.receive(pacote);
            } catch (SocketTimeoutException e) {
                continue;
            } catch (Exception e) {
                if (s.isClosed()) return;
                continue;
            }
            int n = pacote.getLength();
            if (n < 5 || buf[0] != MAGICO[0] || buf[1] != MAGICO[1]
                    || buf[2] != MAGICO[2] || buf[3] != MAGICO[3]) continue;
            int tipo = buf[4] & 0xFF;
            if (tipo == T_RESPOSTA && procurando) {
                String nome = new String(buf, 5, n - 5, Charset.forName("UTF-8"));
                String ip = pacote.getAddress().getHostAddress();
                procurando = false;
                nomePc = nome.isEmpty() ? ip : nome;
                destino = new InetSocketAddress(pacote.getAddress(), PORTA);
                ouvinte.aoEncontrarPc(nomePc, ip);
            } else if (tipo == T_CONFIRMA && n >= 6) {
                jogador = buf[5] & 0xFF;
                ultimaConfirmacao = System.currentTimeMillis();
            } else if (tipo == T_VIBRAR && n >= 7) {
                ouvinte.aoVibrar(buf[5] & 0xFF, buf[6] & 0xFF);
            }
        }
    }

    /** Broadcast de cada rede do aparelho (Wi-Fi e roteador do celular) e o geral. */
    static List<InetAddress> enderecosDeBroadcast() {
        List<InetAddress> lista = new ArrayList<InetAddress>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (ia.getAddress() instanceof Inet4Address && b != null && !lista.contains(b)) {
                        lista.add(b);
                    }
                }
            }
        } catch (Exception ignorada) {
        }
        try {
            lista.add(InetAddress.getByAddress(new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
        } catch (Exception ignorada) {
        }
        return lista;
    }

    private void montarEstado(byte[] buf, int seq) {
        ByteBuffer b = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        b.put(MAGICO);
        b.put((byte) T_ESTADO);
        b.put((byte) 0);
        b.putShort((short) seq);
        synchronized (estado) {
            b.putShort((short) estado.botoes);
            b.put((byte) estado.lt);
            b.put((byte) estado.rt);
            b.putShort(estado.lx);
            b.putShort(estado.ly);
            b.putShort(estado.rx);
            b.putShort(estado.ry);
        }
    }

    private static byte[] cabecalho(int tipo) {
        return new byte[]{MAGICO[0], MAGICO[1], MAGICO[2], MAGICO[3], (byte) tipo};
    }

    private static void enviar(DatagramSocket s, InetSocketAddress d, byte[] dados) {
        if (s == null || d == null) return;
        try {
            s.send(new DatagramPacket(dados, dados.length, d));
        } catch (Exception ignorada) {
            // Wi-Fi caiu ou IP inválido: tenta de novo no próximo ciclo.
        }
    }
}
