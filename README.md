# prumo-mobile

App Android do **Prumo**, o coach de bem-estar com IA que responde "**Como estou hoje?**". Projeto do FMU Tech Inovação 2026.2, alinhado ao ODS 3 (Saúde e Bem-Estar).

**Baixar:** https://download.healthprumo.online

O app empacota o [prumo-web](https://github.com/Health-Prumo/prumo-web) com o [Capacitor](https://capacitorjs.com/). As telas são as mesmas do site `https://app.healthprumo.online`, e a conta também.

## Como é montado

```
prumo-web (tag em prumo-web.ref) ──build do Vite──> web/dist ──Capacitor + Gradle──> saida/prumo-<versão>.apk
                                                                                          │
                                                       scripts/deploy.sh ──> S3 + CloudFront (download.healthprumo.online)
```

| Arquivo | Para quê |
|---|---|
| `prumo-web.ref` | Tag do prumo-web que entra no app |
| `package.json` | Versão do app (`version`) e dependências do Capacitor |
| `capacitor.config.json` | ID do app, nome e origem do WebView |
| `android/` | Projeto Android gerado pelo Capacitor, com assinatura e versão no `app/build.gradle` |
| `assets/` | Fontes do ícone e do splash |
| `site/` | Página de download |
| `scripts/build.sh` | Gera o APK (`debug` ou `release`) |
| `scripts/deploy.sh` | Publica o APK e a página de download |

## Gerar o APK

Requisitos: Node 20+, Java 21 e o Android SDK (`platforms;android-36` e `build-tools`).

```bash
npm ci
cp .env.example .env.production     # preencha o VITE_COGNITO_CLIENT_ID
scripts/build.sh                    # APK de teste
```

O APK de distribuição precisa da chave de assinatura, que fica fora do repositório (ver [AGENTS.md](AGENTS.md)).

## Publicar uma versão nova

1. Se o front mudou: crie a tag no prumo-web e atualize `prumo-web.ref`.
2. Suba o `version` no `package.json`.
3. `scripts/build.sh release` e `scripts/deploy.sh`.
4. Commit e tag `vX.Y.Z` neste repositório.

> O Prumo não substitui orientação médica.
