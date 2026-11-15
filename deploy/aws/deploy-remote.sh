#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
incoming="${1:?incoming directory required}"
case "$incoming" in "$HOME"/fivegrade/incoming-[0-9]*-[0-9]*) ;; *) echo 'Invalid incoming directory'; exit 1 ;; esac
root="$HOME/fivegrade"
cd "$root"
exec 9>"$root/deploy.lock"
flock -n 9 || { echo 'Another deployment is running'; exit 1; }
trap 'rm -rf -- "$incoming"' EXIT
[ -s certs/global-bundle.pem ] || { echo 'RDS CA missing'; exit 1; }
version="$(docker compose version --short | sed 's/^v//')"
[ "$(printf '%s\n' 2.30.0 "$version" | sort -V | head -n 1)" = 2.30.0 ] || { echo 'Compose >=2.30.0 required'; exit 1; }
# Validate before changing the running installation.
mkdir -p "$incoming/certs"
cp certs/global-bundle.pem "$incoming/certs/global-bundle.pem"
docker compose --project-directory "$incoming" -f "$incoming/compose.aws.yaml" config --quiet
gzip -dc "$incoming/image.tar.gz" | docker load
# Keep the previous configuration privately; no automatic DB rollback.
if [ -f .env ]; then
  mkdir -p previous
  for name in .env runtime.env compose.aws.yaml Caddyfile; do
    [ ! -f "$name" ] || cp "$name" "previous/$name"
  done
fi
for name in .env runtime.env compose.aws.yaml Caddyfile; do
  install -m 600 "$incoming/$name" "$root/$name"
done
# Bind mounts must reopen Caddyfile after its replacement.
docker compose -p fivegrade -f compose.aws.yaml up -d --force-recreate
ready=false
for attempt in $(seq 1 90); do
  if curl --silent --fail --max-time 3 http://127.0.0.1:8080/api/majors -o /dev/null; then ready=true; break; fi
  sleep 2
done
if [ "$ready" != true ]; then
  echo 'App health check failed. Inspect EC2 logs privately; do not paste secrets or user data.'
  docker compose -p fivegrade -f compose.aws.yaml ps
  exit 1
fi
# .env contains only validated, non-secret Compose settings.
domain="$(sed -n 's/^API_DOMAIN=//p' .env)"
https_ready=false
for attempt in $(seq 1 30); do
  if curl --silent --fail --max-time 5 "https://$domain/api/majors" -o /dev/null; then https_ready=true; break; fi
  sleep 2
done
[ "$https_ready" = true ] || { echo 'HTTPS check failed; verify DNS, 80/443 and Caddy privately.'; exit 1; }
echo 'Deployment and HTTPS check passed.'
