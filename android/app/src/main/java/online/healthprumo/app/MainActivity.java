package online.healthprumo.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Plugin próprio: aviso e instalação de versão nova (ver AtualizacaoPlugin).
        registerPlugin(AtualizacaoPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
