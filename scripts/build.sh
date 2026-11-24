#!/usr/bin/env bash
# Gera o APK do Prumo a partir do prumo-web na tag fixada em prumo-web.ref.
#
# Uso:
#   scripts/build.sh            # APK de teste (assinado com a chave de debug)
#   scripts/build.sh release    # APK de distribuição (precisa da chave, ver README)
#
# Saída em saida/prumo-<versão>.apk.
set -euo pipefail

cd "$(dirname "$0")/.."

TIPO="${1:-debug}"
REF="$(tr -d '[:space:]' < prumo-web.ref)"
VERSAO="$(node -p "require('./package.json').version")"
REPO_WEB="https://github.com/Health-Prumo/prumo-web.git"

if [[ "$TIPO" != "debug" && "$TIPO" != "release" ]]; then
  echo "Tipo inválido: $TIPO (use debug ou release)." >&2
  exit 1
fi
if [[ ! -f .env.production ]]; then
  echo "Falta o .env.production (copie do .env.example e preencha)." >&2
  exit 1
fi
if [[ "$TIPO" == "release" ]]; then
  : "${PRUMO_KEYSTORE:?Defina PRUMO_KEYSTORE (caminho da chave .jks, ver README)}"
  : "${PRUMO_KEYSTORE_PASSWORD:?Defina PRUMO_KEYSTORE_PASSWORD}"
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

# versionCode: 0.1.0 -> 100, 1.2.3 -> 10203. Precisa sempre crescer para o Android aceitar a atualização.
IFS=. read -r MAJ MIN PAT <<< "$VERSAO"
CODIGO=$(( MAJ * 10000 + MIN * 100 + PAT ))

echo "==> prumo-web $REF"
rm -rf web
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$REF" "$REPO_WEB" web
cp .env.production web/.env.production
(cd web && npm ci --silent && npm run build)

echo "==> Android $VERSAO (versionCode $CODIGO, $TIPO)"
npm ci --silent
npx cap sync android
echo "sdk.dir=$ANDROID_HOME" > android/local.properties

TAREFA="assemble$(tr '[:lower:]' '[:upper:]' <<< "${TIPO:0:1}")${TIPO:1}"
(cd android && ./gradlew "$TAREFA" --console=plain -q \
  -PprumoVersionName="$VERSAO" -PprumoVersionCode="$CODIGO")

mkdir -p saida
SUFIXO=""
[[ "$TIPO" == "debug" ]] && SUFIXO="-debug"
DESTINO="saida/prumo-${VERSAO}${SUFIXO}.apk"
cp "android/app/build/outputs/apk/$TIPO/app-$TIPO.apk" "$DESTINO"
echo "APK pronto: $DESTINO (prumo-web $REF)"
