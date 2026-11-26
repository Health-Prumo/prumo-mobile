package online.healthprumo.app;

import android.os.Bundle;
import android.webkit.WebView;
import androidx.core.splashscreen.SplashScreen;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Splash do sistema (esmeralda + símbolo), em todas as versões do Android.
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        // Plugin próprio: aviso e instalação de versão nova (ver AtualizacaoPlugin).
        registerPlugin(AtualizacaoPlugin.class);
        super.onCreate(savedInstanceState);

        // Abertura animada: assume quando o splash do sistema sai e some quando o app carregou.
        AberturaAnimada abertura = new AberturaAnimada(this);
        abertura.mostrar();
        splash.setOnExitAnimationListener(vista -> {
            vista.remove();
            abertura.iniciar();
        });
        bridge.addWebViewListener(new WebViewListener() {
            @Override
            public void onPageLoaded(WebView webView) {
                abertura.paginaCarregada(webView);
            }
        });
    }
}
