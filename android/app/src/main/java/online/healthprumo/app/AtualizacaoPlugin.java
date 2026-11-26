package online.healthprumo.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/**
 * Atualização do app sem desinstalar: lê o versao.json publicado pelo scripts/deploy.sh,
 * baixa o APK novo, confere o SHA-256 e abre o instalador do Android. O Android só aceita
 * se o APK novo tiver a mesma assinatura (chave prumo-release.jks) e versionCode maior.
 */
@CapacitorPlugin(name = "Atualizacao")
public class AtualizacaoPlugin extends Plugin {

    private static final String BASE = "https://download.healthprumo.online/";
    private static final String MANIFESTO = BASE + "versao.json";
    private static final int LIMITE_APK = 100 * 1024 * 1024;

    private JSONObject ultimo;

    @PluginMethod
    public void verificar(PluginCall call) {
        new Thread(() -> {
            try {
                JSONObject m = new JSONObject(new String(baixar(MANIFESTO + "?t=" + System.currentTimeMillis(), 64 * 1024), StandardCharsets.UTF_8));
                ultimo = m;
                PackageInfo info = pacote();
                long atual = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                JSObject r = new JSObject();
                r.put("disponivel", m.getLong("versionCode") > atual);
                r.put("atual", info.versionName);
                r.put("versao", m.getString("versao"));
                call.resolve(r);
            } catch (Exception e) {
                call.reject("Não foi possível verificar atualizações.", "SEM_CONEXAO", e);
            }
        }).start();
    }

    @PluginMethod
    public void instalar(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
            // Primeira vez: o Android pede para permitir que o Prumo instale atualizações.
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.reject("Permita instalar atualizações e tente de novo.", "PERMISSAO");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject m = ultimo != null ? ultimo
                    : new JSONObject(new String(baixar(MANIFESTO, 64 * 1024), StandardCharsets.UTF_8));
                String apk = m.getString("apk");
                // Só baixa do domínio do Prumo, nunca de uma URL arbitrária.
                if (apk.contains("/") || apk.contains("..") || !apk.endsWith(".apk")) {
                    throw new IllegalStateException("Nome de APK inválido: " + apk);
                }
                byte[] dados = baixar(BASE + apk, LIMITE_APK);
                String hash = sha256(dados);
                if (!hash.equalsIgnoreCase(m.getString("sha256"))) {
                    throw new IllegalStateException("SHA-256 não confere");
                }
                File pasta = new File(getContext().getCacheDir(), "atualizacao");
                pasta.mkdirs();
                File arquivo = new File(pasta, "prumo.apk");
                try (OutputStream out = new FileOutputStream(arquivo)) {
                    out.write(dados);
                }
                Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", arquivo);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
                call.resolve();
            } catch (Exception e) {
                call.reject("Não foi possível baixar a atualização.", "FALHA", e);
            }
        }).start();
    }

    private PackageInfo pacote() throws PackageManager.NameNotFoundException {
        return getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
    }

    private static byte[] baixar(String endereco, int limite) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endereco).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + c.getResponseCode());
            }
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > limite) throw new IllegalStateException("Arquivo grande demais");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String sha256(byte[] dados) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(dados);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
