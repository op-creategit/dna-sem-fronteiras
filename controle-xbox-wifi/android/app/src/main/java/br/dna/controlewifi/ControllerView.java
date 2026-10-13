package br.dna.controlewifi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Desenha o controle no layout do Xbox 360 e transforma os toques
 * (vários dedos ao mesmo tempo) no estado do controle.
 */
public class ControllerView extends View {

    public interface AoTocarStatus {
        void aoTocarStatus();
    }

    private static class Botao {
        final int bit;
        final String rotulo;
        final int cor;
        final boolean gatilho; // LT e RT: retângulo que vale 255 quando apertado
        float cx, cy, r;
        RectF ret;

        Botao(int bit, String rotulo, int cor, boolean gatilho) {
            this.bit = bit;
            this.rotulo = rotulo;
            this.cor = cor;
            this.gatilho = gatilho;
        }

        boolean contem(float x, float y) {
            if (ret != null) {
                float m = ret.height() * 0.2f;
                return x >= ret.left - m && x <= ret.right + m && y >= ret.top - m && y <= ret.bottom + m;
            }
            float dx = x - cx, dy = y - cy, raio = r * 1.25f;
            return dx * dx + dy * dy <= raio * raio;
        }
    }

    private static class Analogico {
        float cx, cy, r;
        float vx, vy; // de -1 a 1, y para baixo como na tela
        int dedo = -1;
    }

    private static final int GATILHO_LT = 1 << 16;
    private static final int GATILHO_RT = 1 << 17;

    private final ControllerState estado;
    private final List<Botao> botoes = new ArrayList<Botao>();
    private final Analogico esquerdo = new Analogico();
    private final Analogico direito = new Analogico();
    private final Botao botaoL3 = new Botao(ControllerState.L3, "L3", Color.GRAY, false);
    private final Botao botaoR3 = new Botao(ControllerState.R3, "R3", Color.GRAY, false);
    private float dpadX, dpadY, dpadBraco;
    private final RectF areaStatus = new RectF();
    private int dedoStatus = -1;
    private int apertados; // bits do XInput mais GATILHO_LT e GATILHO_RT
    private String status = "";
    private boolean conectado;
    private AoTocarStatus aoTocarStatus;

    private final Paint tinta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ControllerView(Context context, ControllerState estado) {
        super(context);
        this.estado = estado;
        setHapticFeedbackEnabled(true);
        texto.setTextAlign(Paint.Align.CENTER);
        texto.setFakeBoldText(true);

        botoes.add(new Botao(ControllerState.A, "A", Color.rgb(0x3D, 0xB3, 0x3D), false));
        botoes.add(new Botao(ControllerState.B, "B", Color.rgb(0xE0, 0x3C, 0x31), false));
        botoes.add(new Botao(ControllerState.X, "X", Color.rgb(0x2A, 0x6F, 0xDB), false));
        botoes.add(new Botao(ControllerState.Y, "Y", Color.rgb(0xF2, 0xC1, 0x2E), false));
        botoes.add(new Botao(ControllerState.BACK, "BACK", Color.LTGRAY, false));
        botoes.add(new Botao(ControllerState.GUIDE, "X", Color.rgb(0x10, 0x7C, 0x10), false));
        botoes.add(new Botao(ControllerState.START, "START", Color.LTGRAY, false));
        botoes.add(new Botao(ControllerState.LB, "LB", Color.LTGRAY, false));
        botoes.add(new Botao(ControllerState.RB, "RB", Color.LTGRAY, false));
        botoes.add(new Botao(GATILHO_LT, "LT", Color.LTGRAY, true));
        botoes.add(new Botao(GATILHO_RT, "RT", Color.LTGRAY, true));
        botoes.add(botaoL3);
        botoes.add(botaoR3);
    }

    public void setAoTocarStatus(AoTocarStatus ouvinte) {
        aoTocarStatus = ouvinte;
    }

    public void setStatus(String status, boolean conectado) {
        if (status.equals(this.status) && conectado == this.conectado) return;
        this.status = status;
        this.conectado = conectado;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float s = Math.min(h, w / 2f);

        esquerdo.cx = 0.15f * w;
        esquerdo.cy = 0.52f * h;
        esquerdo.r = 0.17f * s;
        direito.cx = 0.67f * w;
        direito.cy = 0.78f * h;
        direito.r = 0.15f * s;

        dpadX = 0.33f * w;
        dpadY = 0.78f * h;
        dpadBraco = 0.085f * s;

        float abxyX = 0.85f * w, abxyY = 0.50f * h, d = 0.12f * s, rb = 0.065f * s;
        posicionar(ControllerState.A, abxyX, abxyY + d, rb);
        posicionar(ControllerState.B, abxyX + d, abxyY, rb);
        posicionar(ControllerState.X, abxyX - d, abxyY, rb);
        posicionar(ControllerState.Y, abxyX, abxyY - d, rb);
        posicionar(ControllerState.BACK, 0.41f * w, 0.50f * h, 0.045f * s);
        posicionar(ControllerState.GUIDE, 0.50f * w, 0.50f * h, 0.06f * s);
        posicionar(ControllerState.START, 0.59f * w, 0.50f * h, 0.045f * s);
        posicionar(ControllerState.L3, esquerdo.cx, 0.88f * h, 0.05f * s);
        posicionar(ControllerState.R3, abxyX, 0.88f * h, 0.05f * s);

        retangulo(GATILHO_LT, 0.02f * w, 0.03f * h, 0.20f * w, 0.14f * h);
        retangulo(ControllerState.LB, 0.02f * w, 0.17f * h, 0.20f * w, 0.26f * h);
        retangulo(GATILHO_RT, 0.80f * w, 0.03f * h, 0.98f * w, 0.14f * h);
        retangulo(ControllerState.RB, 0.80f * w, 0.17f * h, 0.98f * w, 0.26f * h);

        areaStatus.set(0.25f * w, 0, 0.75f * w, 0.30f * h);
    }

    private void posicionar(int bit, float cx, float cy, float r) {
        for (Botao b : botoes) {
            if (b.bit == bit) {
                b.cx = cx;
                b.cy = cy;
                b.r = r;
            }
        }
    }

    private void retangulo(int bit, float l, float t, float r, float b) {
        for (Botao bt : botoes) {
            if (bt.bit == bit) bt.ret = new RectF(l, t, r, b);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int acao = e.getActionMasked();
        int idx = e.getActionIndex();
        int soltou = -1;

        if (acao == MotionEvent.ACTION_CANCEL) {
            esquerdo.dedo = -1;
            direito.dedo = -1;
            dedoStatus = -1;
            aplicar(0, esquerdo, direito);
            return true;
        }
        if (acao == MotionEvent.ACTION_DOWN || acao == MotionEvent.ACTION_POINTER_DOWN) {
            int id = e.getPointerId(idx);
            float x = e.getX(idx), y = e.getY(idx);
            if (esquerdo.dedo < 0 && perto(esquerdo, x, y)) {
                esquerdo.dedo = id;
            } else if (direito.dedo < 0 && perto(direito, x, y)) {
                direito.dedo = id;
            } else if (areaStatus.contains(x, y) && !algumBotao(x, y)) {
                dedoStatus = id;
            }
        } else if (acao == MotionEvent.ACTION_UP || acao == MotionEvent.ACTION_POINTER_UP) {
            soltou = e.getPointerId(idx);
        }

        int novos = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            int id = e.getPointerId(i);
            if (id == soltou) continue;
            float x = e.getX(i), y = e.getY(i);
            if (id == esquerdo.dedo) {
                mover(esquerdo, x, y);
            } else if (id == direito.dedo) {
                mover(direito, x, y);
            } else if (id != dedoStatus) {
                novos |= tocados(x, y);
            }
        }

        if (soltou >= 0) {
            if (soltou == esquerdo.dedo) {
                esquerdo.dedo = -1;
                esquerdo.vx = esquerdo.vy = 0;
            } else if (soltou == direito.dedo) {
                direito.dedo = -1;
                direito.vx = direito.vy = 0;
            } else if (soltou == dedoStatus) {
                dedoStatus = -1;
                if (areaStatus.contains(e.getX(idx), e.getY(idx)) && aoTocarStatus != null) {
                    aoTocarStatus.aoTocarStatus();
                }
            }
        }
        aplicar(novos, esquerdo, direito);
        return true;
    }

    private boolean perto(Analogico a, float x, float y) {
        float dx = x - a.cx, dy = y - a.cy, raio = a.r * 1.6f;
        return dx * dx + dy * dy <= raio * raio;
    }

    private void mover(Analogico a, float x, float y) {
        float vx = (x - a.cx) / a.r, vy = (y - a.cy) / a.r;
        float tam = (float) Math.sqrt(vx * vx + vy * vy);
        if (tam > 1) {
            vx /= tam;
            vy /= tam;
        }
        a.vx = vx;
        a.vy = vy;
    }

    private boolean algumBotao(float x, float y) {
        return tocados(x, y) != 0;
    }

    private int tocados(float x, float y) {
        int bits = 0;
        for (Botao b : botoes) {
            if (b.contem(x, y)) bits |= b.bit;
        }
        float dx = x - dpadX, dy = y - dpadY, limite = dpadBraco * 1.8f, zona = dpadBraco * 0.5f;
        if (Math.abs(dx) <= limite && Math.abs(dy) <= limite) {
            if (dx < -zona) bits |= ControllerState.ESQUERDA;
            if (dx > zona) bits |= ControllerState.DIREITA;
            if (dy < -zona) bits |= ControllerState.CIMA;
            if (dy > zona) bits |= ControllerState.BAIXO;
        }
        return bits;
    }

    private void aplicar(int novos, Analogico esq, Analogico dir) {
        if ((novos & ~apertados) != 0) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        apertados = novos;
        synchronized (estado) {
            estado.botoes = novos & 0xFFFF;
            estado.lt = (novos & GATILHO_LT) != 0 ? 255 : 0;
            estado.rt = (novos & GATILHO_RT) != 0 ? 255 : 0;
            estado.lx = eixo(esq.vx);
            estado.ly = eixo(-esq.vy);
            estado.rx = eixo(dir.vx);
            estado.ry = eixo(-dir.vy);
        }
        invalidate();
    }

    private static short eixo(float v) {
        return (short) Math.round(Math.max(-1f, Math.min(1f, v)) * 32767f);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.rgb(0x16, 0x16, 0x18));
        float s = Math.min(getHeight(), getWidth() / 2f);

        desenharAnalogico(c, esquerdo);
        desenharAnalogico(c, direito);
        desenharDpad(c);
        for (Botao b : botoes) desenharBotao(c, b);

        texto.setColor(conectado ? Color.rgb(0x6C, 0xD8, 0x6C) : Color.rgb(0xF2, 0xC1, 0x2E));
        texto.setTextSize(0.055f * s);
        c.drawText(status, areaStatus.centerX(), 0.11f * getHeight(), texto);
        texto.setColor(Color.GRAY);
        texto.setTextSize(0.04f * s);
        c.drawText("toque aqui para trocar de PC", areaStatus.centerX(), 0.19f * getHeight(), texto);
    }

    private void desenharAnalogico(Canvas c, Analogico a) {
        tinta.setStyle(Paint.Style.FILL);
        tinta.setColor(Color.rgb(0x2A, 0x2A, 0x2E));
        c.drawCircle(a.cx, a.cy, a.r * 1.15f, tinta);
        tinta.setStyle(Paint.Style.STROKE);
        tinta.setStrokeWidth(a.r * 0.04f);
        tinta.setColor(Color.rgb(0x55, 0x55, 0x5A));
        c.drawCircle(a.cx, a.cy, a.r * 1.15f, tinta);
        tinta.setStyle(Paint.Style.FILL);
        tinta.setColor(a.dedo >= 0 ? Color.rgb(0x8A, 0x8A, 0x90) : Color.rgb(0x60, 0x60, 0x66));
        c.drawCircle(a.cx + a.vx * a.r * 0.6f, a.cy + a.vy * a.r * 0.6f, a.r * 0.55f, tinta);
    }

    private void desenharDpad(Canvas c) {
        float a = dpadBraco, m = a / 2f;
        int bits = apertados;
        braco(c, dpadX - m, dpadY - m - a, (bits & ControllerState.CIMA) != 0);
        braco(c, dpadX - m, dpadY + m, (bits & ControllerState.BAIXO) != 0);
        braco(c, dpadX - m - a, dpadY - m, (bits & ControllerState.ESQUERDA) != 0);
        braco(c, dpadX + m, dpadY - m, (bits & ControllerState.DIREITA) != 0);
        tinta.setStyle(Paint.Style.FILL);
        tinta.setColor(Color.rgb(0x44, 0x44, 0x4A));
        c.drawRect(dpadX - m, dpadY - m, dpadX + m, dpadY + m, tinta);
    }

    private void braco(Canvas c, float l, float t, boolean apertado) {
        tinta.setStyle(Paint.Style.FILL);
        tinta.setColor(apertado ? Color.rgb(0xB0, 0xB0, 0xB8) : Color.rgb(0x44, 0x44, 0x4A));
        c.drawRect(l, t, l + dpadBraco, t + dpadBraco, tinta);
    }

    private void desenharBotao(Canvas c, Botao b) {
        boolean apertado = (apertados & b.bit) != 0;
        tinta.setStyle(Paint.Style.FILL);
        if (b.ret != null) {
            float raio = b.ret.height() * 0.3f;
            tinta.setColor(apertado ? Color.rgb(0x9A, 0x9A, 0xA2) : Color.rgb(0x33, 0x33, 0x38));
            c.drawRoundRect(b.ret, raio, raio, tinta);
            texto.setColor(apertado ? Color.BLACK : Color.LTGRAY);
            texto.setTextSize(b.ret.height() * 0.5f);
            c.drawText(b.rotulo, b.ret.centerX(), b.ret.centerY() + b.ret.height() * 0.18f, texto);
            return;
        }
        tinta.setColor(apertado ? b.cor : Color.rgb(0x2E, 0x2E, 0x33));
        c.drawCircle(b.cx, b.cy, b.r, tinta);
        tinta.setStyle(Paint.Style.STROKE);
        tinta.setStrokeWidth(b.r * 0.08f);
        tinta.setColor(b.cor);
        c.drawCircle(b.cx, b.cy, b.r, tinta);
        texto.setColor(apertado ? Color.BLACK : b.cor);
        float tam = b.rotulo.length() > 2 ? b.r * 0.55f : b.r * 0.9f;
        texto.setTextSize(tam);
        c.drawText(b.rotulo, b.cx, b.cy + tam * 0.35f, texto);
    }
}
