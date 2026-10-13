package br.dna.controlewifi;

/** Estado atual do controle, no formato do XInput. Acesse com synchronized. */
public class ControllerState {

    public static final int CIMA = 0x0001;
    public static final int BAIXO = 0x0002;
    public static final int ESQUERDA = 0x0004;
    public static final int DIREITA = 0x0008;
    public static final int START = 0x0010;
    public static final int BACK = 0x0020;
    public static final int L3 = 0x0040;
    public static final int R3 = 0x0080;
    public static final int LB = 0x0100;
    public static final int RB = 0x0200;
    public static final int GUIDE = 0x0400;
    public static final int A = 0x1000;
    public static final int B = 0x2000;
    public static final int X = 0x4000;
    public static final int Y = 0x8000;

    public int botoes;
    public int lt;
    public int rt;
    public short lx;
    public short ly;
    public short rx;
    public short ry;
}
