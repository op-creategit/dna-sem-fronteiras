package br.dna.controlewifi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements UdpLink.Ouvinte {

    private static final String PREF_IP = "ip";

    private final ControllerState estado = new ControllerState();
    private final Handler handler = new Handler();
    private ControllerView tela;
    private UdpLink link;
    private Vibrator vibrador;
    private SharedPreferences prefs;
    private int ultimaVibracao;

    private final Runnable atualizarStatus = new Runnable() {
        @Override
        public void run() {
            mostrarStatus();
            handler.postDelayed(this, 300);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getPreferences(Context.MODE_PRIVATE);
        vibrador = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        tela = new ControllerView(this, estado);
        tela.setAoTocarStatus(new ControllerView.AoTocarStatus() {
            @Override
            public void aoTocarStatus() {
                abrirConexao();
            }
        });
        setContentView(tela);

        link = new UdpLink(estado, this);
        String ip = prefs.getString(PREF_IP, "");
        if (ip.isEmpty()) {
            link.procurar();
        } else {
            link.conectar(ip);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        telaCheia();
        link.iniciar();
        handler.post(atualizarStatus);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(atualizarStatus);
        link.parar();
        pararVibracao();
    }

    @Override
    public void onWindowFocusChanged(boolean temFoco) {
        super.onWindowFocusChanged(temFoco);
        if (temFoco) telaCheia();
    }

    private void telaCheia() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void mostrarStatus() {
        int jogador = link.getJogador();
        String pc = link.getNomePc();
        if (link.estaProcurando()) {
            tela.setStatus("Procurando o PC na rede...", false);
        } else if (jogador > 0) {
            tela.setStatus("Jogador " + jogador + " em " + pc, true);
        } else if (jogador == 0) {
            tela.setStatus(pc + " está cheio ou sem driver", false);
        } else {
            tela.setStatus("Esperando resposta de " + pc + "...", false);
        }
    }

    private void abrirConexao() {
        float d = getResources().getDisplayMetrics().density;
        int p = (int) (20 * d);
        LinearLayout caixa = new LinearLayout(this);
        caixa.setOrientation(LinearLayout.VERTICAL);
        caixa.setPadding(p, p / 2, p, 0);

        TextView ajuda = new TextView(this);
        ajuda.setText("Abra o servidor no PC. Ele mostra o IP a digitar aqui. "
                + "Celular e PC precisam estar na mesma rede Wi-Fi.");
        caixa.addView(ajuda);

        final EditText campo = new EditText(this);
        campo.setHint("IP do PC, ex.: 192.168.0.10");
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        campo.setSingleLine(true);
        campo.setText(prefs.getString(PREF_IP, ""));
        caixa.addView(campo);

        new AlertDialog.Builder(this)
                .setTitle("Conectar ao PC")
                .setView(caixa)
                .setPositiveButton("Conectar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int i) {
                        String ip = campo.getText().toString().trim();
                        if (ip.isEmpty()) {
                            procurarAutomatico();
                            return;
                        }
                        prefs.edit().putString(PREF_IP, ip).apply();
                        link.conectar(ip);
                    }
                })
                .setNeutralButton("Procurar sozinho", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface di, int i) {
                        procurarAutomatico();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface di) {
                        telaCheia();
                    }
                })
                .show();
    }

    private void procurarAutomatico() {
        prefs.edit().remove(PREF_IP).apply();
        link.procurar();
    }

    @Override
    public void aoEncontrarPc(String nome, String ip) {
        // Chamado na thread de rede; o status é atualizado pelo timer.
    }

    @Override
    public void aoVibrar(int motorGrande, int motorPequeno) {
        int forca = Math.max(motorGrande, motorPequeno);
        if (forca == ultimaVibracao || vibrador == null || !vibrador.hasVibrator()) return;
        ultimaVibracao = forca;
        if (forca == 0) {
            vibrador.cancel();
        } else if (Build.VERSION.SDK_INT >= 26) {
            vibrador.vibrate(VibrationEffect.createOneShot(10000, Math.max(1, forca)));
        } else {
            vibrador.vibrate(10000);
        }
    }

    private void pararVibracao() {
        ultimaVibracao = 0;
        if (vibrador != null) vibrador.cancel();
    }
}
