#!/bin/sh
# Generates a dev CA, a server cert for the core, and a client cert for the agent,
# plus the PKCS12 keystore/truststore the core needs. Dev use only.
set -e
cd "$(dirname "$0")"
mkdir -p out
KS_PASS="${KS_PASS:-changeit}"
TS_PASS="${TS_PASS:-changeit}"

echo "==> CA"
openssl req -x509 -newkey rsa:4096 -nodes -keyout out/ca.key -out out/ca.crt \
  -days 3650 -subj "/CN=GridVeritas Dev CA"

echo "==> Server cert (core)"
openssl req -newkey rsa:2048 -nodes -keyout out/server.key -out out/server.csr \
  -subj "/CN=gridveritas-core"
cat > out/server.ext <<EXT
subjectAltName=DNS:gridveritas-core,DNS:localhost,IP:127.0.0.1
extendedKeyUsage=serverAuth
EXT
openssl x509 -req -in out/server.csr -CA out/ca.crt -CAkey out/ca.key -CAcreateserial \
  -out out/server.crt -days 825 -extfile out/server.ext

echo "==> Client cert (agent)"
openssl req -newkey rsa:2048 -nodes -keyout out/agent.key -out out/agent.csr \
  -subj "/CN=edge-agent-01"
cat > out/agent.ext <<EXT
extendedKeyUsage=clientAuth
EXT
openssl x509 -req -in out/agent.csr -CA out/ca.crt -CAkey out/ca.key -CAcreateserial \
  -out out/agent.crt -days 825 -extfile out/agent.ext

echo "==> Core keystore (server key+cert) and truststore (CA)"
openssl pkcs12 -export -inkey out/server.key -in out/server.crt -certfile out/ca.crt \
  -name core -out out/core-keystore.p12 -passout pass:"$KS_PASS"
openssl pkcs12 -export -nokeys -in out/ca.crt -name ca \
  -out out/truststore.p12 -passout pass:"$TS_PASS"

echo "Done. Artifacts in $(pwd)/out"
echo "  core:  core-keystore.p12, truststore.p12 (pass: $KS_PASS / $TS_PASS)"
echo "  agent: agent.crt, agent.key, ca.crt"
