# AGENTS.md — prumo-mobile

App Android do **Prumo**, o coach de bem-estar com IA que responde "como estou hoje?" (FMU Tech Inovação 2026.2, ODS 3).

**Antes de qualquer tarefa, leia o contexto geral** em [Health-Prumo/prumo/AGENTS.md](https://github.com/Health-Prumo/prumo/blob/main/AGENTS.md): o que é o Prumo, decisões, identidade visual, LGPD, AWS e convenções. Este arquivo traz só o que é específico do app.

## Como funciona

- **Este repositório não tem telas.** Ele empacota o [prumo-web](https://github.com/Health-Prumo/prumo-web) num APK com o **Capacitor 8**.
- A versão do front fica fixada **por tag** em `prumo-web.ref` (hoje `v0.4.0`). O `scripts/build.sh` clona o prumo-web nessa tag em `web/`, roda o build do Vite e monta o APK. É o mesmo padrão do `tf-environments` com o `tf-blueprint`.
- **Mudança de tela ou texto vai no prumo-web**, nunca aqui. Para levar ao app: tag nova no prumo-web, atualize `prumo-web.ref`, suba a versão no `package.json` e publique.
- O app usa a mesma API (`https://api.healthprumo.online`) e o mesmo Cognito do site.

## Decisões

| Item | Valor | Por quê |
|---|---|---|
| ID do app | `online.healthprumo.app` | Domínio do projeto ao contrário. **Não mude**: o Android trata outro ID como outro app |
| Origem do WebView | `https://app.healthprumo.online` (`server.hostname` no `capacitor.config.json`) | Os arquivos são locais (vêm do APK), mas a origem é a do site, então o CORS da API já aceita sem mudar a infra |
| Versão | `version` do `package.json`; `versionCode = maior*10000 + menor*100 + correção` | O `versionCode` precisa sempre crescer para o Android aceitar a atualização |
| Android mínimo | 7.0 (API 24), alvo API 36 | Padrão do Capacitor 8 |
| Distribuição | APK em `https://download.healthprumo.online` (S3 + CloudFront, unidade `dev/mobile` do `tf-environments`) | Sem Play Store por enquanto |

## Atualização dentro do app

- O plugin nativo `AtualizacaoPlugin` (em `android/app/src/main/java/online/healthprumo/app/`, registrado no `MainActivity`) lê `https://download.healthprumo.online/versao.json`, que o `scripts/deploy.sh` publica com `versao`, `versionCode`, `apk` e `sha256`.
- Se o `versionCode` publicado for maior que o instalado, o prumo-web mostra o aviso "Nova versão do Prumo" (`src/components/AvisoAtualizacao.tsx`). No toque em **Atualizar**, o plugin baixa o APK, confere o SHA-256 e abre o instalador do Android.
- Na primeira vez, o Android pede para permitir que o Prumo instale apps (permissão `REQUEST_INSTALL_PACKAGES`). Fora da Play Store, a tela de confirmação do instalador sempre aparece: não dá para atualizar em silêncio.
- A atualização mantém os dados e o login. Só funciona com a **mesma chave de assinatura** e `versionCode` maior.
- Vale a partir da 0.3.0. Quem tem uma versão anterior instala a 0.3.0 por cima, pelo site, sem desinstalar.

## Chave de assinatura

- O APK de distribuição é assinado com a chave `prumo-release.jks` (alias `prumo`). **Ela nunca entra no repositório.**
- Fica fora dele, com as senhas num `keystore.env` (por exemplo em `~/.config/prumo/`), carregado com `source` antes do build.
- **Sem a mesma chave, o celular não aceita a atualização**: o usuário teria que desinstalar e perder a sessão. Guarde uma cópia segura (gerenciador de senhas do grupo).
- Impressão digital SHA-256 do certificado: `51:5E:DA:62:80:C2:E8:C3:B0:EA:97:77:E5:F8:B8:74:14:44:A3:79:30:09:47:6D:90:41:58:7E:00:54:C9:25`.

## Comandos

```bash
npm ci
cp .env.example .env.production          # preencha o VITE_COGNITO_CLIENT_ID (terragrunt output de dev/auth)
scripts/build.sh                         # APK de teste: saida/prumo-<versão>-debug.apk
source ~/.config/prumo/keystore.env
scripts/build.sh release                 # APK de distribuição: saida/prumo-<versão>.apk
BUCKET=... DISTRIBUTION_ID=... scripts/deploy.sh   # publica em download.healthprumo.online
```

Requisitos: Node 20+, Java 21 e o Android SDK (`ANDROID_HOME`, padrão `~/Android/Sdk`, com `platforms;android-36` e `build-tools`).

## Abertura animada

- Igual em todas as versões (Android 7 em diante). O splash do sistema (`androidx core-splashscreen`, tema `AppTheme.NoActionBarLaunch`) mostra o esmeralda com o símbolo parado. Logo depois, a `AberturaAnimada` (camada nativa por cima do WebView) assume na mesma posição: o fio de prumo balança e para alinhado, e o nome "Prumo" aparece no rodapé.
- A camada só sai quando o React já desenhou a primeira tela (confere o `#root` do WebView) e depois de pelo menos 1,5 s. Nunca passa de 5 s.
- Desenhos em `android/app/src/main/res/drawable/`: `splash_simbolo.xml` (símbolo parado), `splash_simbolo_animado.xml` (balanço do fio) e `splash_marca.xml` (nome). Vêm do `simbolo.svg` e do `logo-horizontal.svg` do repositório `prumo`. Se a marca mudar, gere de novo a partir deles.
- Os `splash.png` gerados pelo `npm run icones` não são mais usados.

## Ícone e splash

- Fontes em `assets/` (geradas a partir de `assets/brand/png/icone-app-1024.png` do repositório `prumo`). `npm run icones` regenera os arquivos do Android.
- Depois de regenerar, volte o `android:inset` do `ic_launcher_background` para `0%` em `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher*.xml`: o gerador põe 16,7% e o fundo fica com borda.

## Regras

- Mesmas regras do front: o app **não calcula nota nem gera texto de IA**, não guarda registros além da sessão e não envia dados a terceiros.
- Nada de segredo no `.env.production`: as variáveis `VITE_*` vão para dentro do APK, e qualquer um pode extrair.
- Não commite `web/`, `saida/`, APKs, `.jks`, `.env.production` nem `android/local.properties`.
- Commits em português, no imperativo, com a identidade git pessoal do integrante.
- CI: build do APK de teste e `gitleaks` a cada push e pull request; Dependabot para npm, Gradle e Actions.
