package online.healthprumo.app;

import android.app.Activity;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Abertura animada do app, igual em todas as versões do Android (7 em diante).
 *
 * Cobre a janela inteira com o esmeralda e o símbolo na mesma posição do splash do sistema.
 * Quando o splash do sistema sai, o fio de prumo balança e para alinhado e o nome "Prumo"
 * aparece no rodapé. Some com um fade quando o WebView termina de carregar (e a animação
 * terminou) e o React desenhou a primeira tela, para não mostrar a tela vazia do carregamento.
 */
class AberturaAnimada {

    /** Tempo mínimo na tela: o balanço do fio dura ~1,4 s. */
    private static final long MINIMO_MS = 1500;
    /** Nunca segura o usuário mais que isso, mesmo sem internet. */
    private static final long MAXIMO_MS = 5000;
    /** Intervalo para conferir se o React já desenhou a primeira tela. */
    private static final long CONFERIR_MS = 100;
    /** Script que diz se o React já pôs algo dentro do #root. */
    private static final String JS_PRONTO =
        "(function(){var r=document.getElementById('root');return !!(r&&r.children.length);})()";

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout camada;
    private ImageView simbolo;
    private ImageView marca;
    private long inicio = -1;
    private boolean webPronto = false;
    private boolean fechando = false;

    AberturaAnimada(Activity activity) {
        this.activity = activity;
    }

    /** Põe a camada por cima de tudo (chamar no onCreate, antes do primeiro quadro). */
    void mostrar() {
        float dp = activity.getResources().getDisplayMetrics().density;

        camada = new FrameLayout(activity);
        camada.setBackgroundColor(ContextCompat.getColor(activity, R.color.prumoEsmeralda));
        camada.setClickable(true); // não deixa toques passarem para o app por baixo
        camada.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        simbolo = new ImageView(activity);
        simbolo.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.splash_simbolo_animado));
        int lado = Math.round(288 * dp);
        camada.addView(simbolo, new FrameLayout.LayoutParams(lado, lado, Gravity.CENTER));

        marca = new ImageView(activity);
        marca.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.splash_marca));
        marca.setAlpha(0f);
        marca.setTranslationY(12 * dp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int base = Math.round(48 * dp);
        lp.bottomMargin = base;
        camada.addView(marca, lp);

        // Nome acima da barra de navegação (gestos ou 3 botões).
        ViewCompat.setOnApplyWindowInsetsListener(camada, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) marca.getLayoutParams();
            p.bottomMargin = base + barras.bottom;
            marca.setLayoutParams(p);
            return insets;
        });

        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(camada, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Garantia: se o splash do sistema não avisar a saída, começa assim mesmo.
        handler.postDelayed(this::iniciar, 1000);
    }

    /** Começa a animação (quando o splash do sistema sai). */
    void iniciar() {
        if (inicio >= 0 || camada == null) return;
        inicio = SystemClock.uptimeMillis();
        Drawable d = simbolo.getDrawable();
        if (d instanceof Animatable) ((Animatable) d).start();
        marca.animate().alpha(1f).translationY(0f).setStartDelay(300).setDuration(600).start();
        handler.postDelayed(this::tentarFechar, MINIMO_MS);
        handler.postDelayed(this::fechar, MAXIMO_MS);
    }

    /** O WebView carregou o HTML: espera o React desenhar a primeira tela. */
    void paginaCarregada(WebView webView) {
        if (fechando) return;
        webView.evaluateJavascript(JS_PRONTO, resultado -> {
            if ("true".equals(resultado)) {
                // Mais um quadro para a tela aparecer de fato antes do fade.
                handler.postDelayed(() -> {
                    webPronto = true;
                    tentarFechar();
                }, CONFERIR_MS);
            } else {
                handler.postDelayed(() -> paginaCarregada(webView), CONFERIR_MS);
            }
        });
    }

    private void tentarFechar() {
        if (inicio < 0 || !webPronto) return;
        if (SystemClock.uptimeMillis() - inicio >= MINIMO_MS) fechar();
    }

    private void fechar() {
        if (fechando || camada == null) return;
        fechando = true;
        handler.removeCallbacksAndMessages(null);
        simbolo.animate().scaleX(1.08f).scaleY(1.08f).setDuration(350).start();
        camada.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            ViewGroup pai = (ViewGroup) camada.getParent();
            if (pai != null) pai.removeView(camada);
            camada = null;
        }).start();
    }
}
