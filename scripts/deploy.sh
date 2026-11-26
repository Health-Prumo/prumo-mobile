#!/usr/bin/env bash
# Publica o APK de distribuição em https://download.healthprumo.online (S3 + CloudFront).
#
# Uso (depois de scripts/build.sh release):
#   BUCKET=prumo-dev-download-444331926746 DISTRIBUTION_ID=E123ABC scripts/deploy.sh
#
# Os valores saem de `terragrunt output` na unidade dev/mobile do tf-environments.
set -euo pipefail

CONTA_PRUMO="444331926746"
PERFIL="${AWS_PROFILE_PRUMO:-prumo}"

: "${BUCKET:?Defina BUCKET (terragrunt output bucket_name)}"
: "${DISTRIBUTION_ID:?Defina DISTRIBUTION_ID (terragrunt output distribution_id)}"

cd "$(dirname "$0")/.."

VERSAO="$(node -p "require('./package.json').version")"
REF="$(tr -d '[:space:]' < prumo-web.ref)"
APK="saida/prumo-${VERSAO}.apk"

if [[ ! -f "$APK" ]]; then
  echo "Falta o $APK. Rode antes: scripts/build.sh release" >&2
  exit 1
fi

conta="$(aws sts get-caller-identity --profile "$PERFIL" --query Account --output text)"
if [[ "$conta" != "$CONTA_PRUMO" ]]; then
  echo "Conta errada: $conta (esperado $CONTA_PRUMO). Rode: aws login --profile $PERFIL" >&2
  exit 1
fi

TIPO_APK="application/vnd.android.package-archive"
SHA256="$(sha256sum "$APK" | cut -d' ' -f1)"
TAMANHO="$(du -m "$APK" | cut -f1)"
DATA="$(date +%d/%m/%Y)"

# Versão fixa (nunca muda): cache longo.
aws s3 cp "$APK" "s3://$BUCKET/prumo-${VERSAO}.apk" --profile "$PERFIL" \
  --content-type "$TIPO_APK" --content-disposition "attachment; filename=prumo-${VERSAO}.apk" \
  --cache-control "public, max-age=31536000, immutable"

# prumo.apk: sempre a última versão.
aws s3 cp "$APK" "s3://$BUCKET/prumo.apk" --profile "$PERFIL" \
  --content-type "$TIPO_APK" --content-disposition "attachment; filename=prumo-${VERSAO}.apk" \
  --cache-control "no-cache"

# versao.json: o app lê para avisar que há versão nova (AtualizacaoPlugin). Vai depois dos APKs,
# para o aviso nunca apontar para um arquivo que ainda não subiu.
IFS=. read -r MAJ MIN PAT <<< "$VERSAO"
CODIGO=$(( MAJ * 10000 + MIN * 100 + PAT ))
manifesto="$(mktemp)"
printf '{"versao":"%s","versionCode":%d,"apk":"prumo-%s.apk","sha256":"%s"}\n' \
  "$VERSAO" "$CODIGO" "$VERSAO" "$SHA256" > "$manifesto"
aws s3 cp "$manifesto" "s3://$BUCKET/versao.json" --profile "$PERFIL" \
  --cache-control "no-cache" --content-type "application/json"
rm -f "$manifesto"

# Página de download com versão e hash preenchidos.
pagina="$(mktemp)"
sed -e "s/{{VERSAO}}/$VERSAO/g" -e "s/{{WEB_REF}}/$REF/g" -e "s/{{SHA256}}/$SHA256/g" \
  -e "s/{{TAMANHO}}/$TAMANHO/g" -e "s#{{DATA}}#$DATA#g" site/index.html > "$pagina"
aws s3 cp "$pagina" "s3://$BUCKET/index.html" --profile "$PERFIL" \
  --cache-control "no-cache" --content-type "text/html; charset=utf-8"
aws s3 cp site/simbolo.svg "s3://$BUCKET/simbolo.svg" --profile "$PERFIL" \
  --cache-control "public, max-age=86400" --content-type "image/svg+xml"
rm -f "$pagina"

aws cloudfront create-invalidation --profile "$PERFIL" \
  --distribution-id "$DISTRIBUTION_ID" --paths "/" "/index.html" "/prumo.apk" "/versao.json" >/dev/null

echo "Publicado: https://download.healthprumo.online/prumo.apk (v$VERSAO, sha256 $SHA256)"
