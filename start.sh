#!/bin/bash
# Always build a fresh fat-jar before running. This guarantees the running
# code matches the current source tree -- without this, an old
# `target/DukaanLocker-0.0.1-SNAPSHOT.jar` from a previous refactor can be
# launched by accident and produce confusing 404s on Swagger and the new
# /docs/shops/.../fssai-food-license/upload endpoint.
#
# `set -e` makes the script bail on any failure (missing .env, mvn build
# error, port collision). `.env` is sourced ONLY around the `java` invocation
# so secrets (DB_PASSWORD, AWS_*) are not exported into the mvnw JVM where
# plugins like git-commit-id or surefire could leak them in debug output.
set -e
cd "$(dirname "$0")"

./mvnw -q -B -DskipTests package

# Refuse to start if port 8081 is already taken -- surfaces the actual
# blocking PID instead of letting `java -jar` fail with a generic BindException.
if command -v lsof >/dev/null 2>&1 && lsof -i :8081 >/dev/null 2>&1; then
  echo "ERROR: port 8081 is already in use:" >&2
  lsof -i :8081 >&2 || true
  exit 1
elif command -v ss >/dev/null 2>&1 && ss -ltn | awk '{print $4}' | grep -qE '[:.]8081$'; then
  echo "ERROR: port 8081 is already in use." >&2
  ss -ltnp | grep ':8081' >&2 || true
  exit 1
fi

set -a; source .env; set +a

# Auto-detect the EC2 public IPv4 if EXTERNAL_URL wasn't set in .env. The
# 169.254.169.254 IMDS endpoint is reachable only from inside an EC2 instance;
# on any other host (laptop, CI, dev box) it times out and we silently fall
# back to the localhost default so `app.external-url` resolves sensibly.
# Set SKIP_EC2_AUTODETECT=1 in .env if you want to disable this entirely (e.g.,
# you're on EC2 behind a TLS-terminating ALB/CloudFront and want EXTERNAL_URL
# to stay unset so you can set it explicitly to https://...).
# The dotted-quad + octet-range regex guards against an HTML error page
# masquerading as the answer (IMDSv2-failure responses, VM reboots, etc.) AND
# rejects impossible octet ranges (e.g. 999.0.0.1).
if [ -z "${EXTERNAL_URL:-}" ] && [ "${SKIP_EC2_AUTODETECT:-0}" != "1" ]; then
  ec2_ip=$(curl -s --max-time 2 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)
  if [ -n "$ec2_ip" ] \
      && [[ "$ec2_ip" =~ ^([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})\.([0-9]{1,3})$ ]] \
      && [ "${BASH_REMATCH[1]}" -le 255 ] && [ "${BASH_REMATCH[2]}" -le 255 ] \
      && [ "${BASH_REMATCH[3]}" -le 255 ] && [ "${BASH_REMATCH[4]}" -le 255 ]; then
    export EXTERNAL_URL="http://${ec2_ip}:8081"
    echo "EC2 detected: EXTERNAL_URL=$EXTERNAL_URL"
  fi
fi

# Hand a clean hint to whoever SSH'd in: which URLs to point a browser at.
echo "  Spring Boot is starting on http://0.0.0.0:8081 (binds all interfaces)"
echo "  Swagger UI : http://${EXTERNAL_URL:-http://localhost:8081}/swagger-ui/index.html"
echo "  OpenAPI    : http://${EXTERNAL_URL:-http://localhost:8081}/v3/api-docs"
echo "  (Override EXTERNAL_URL in .env if you're not on EC2 and don't want the auto-detected IP.)"

java -jar target/DukaanLocker-0.0.1-SNAPSHOT.jar --server.port=8081
