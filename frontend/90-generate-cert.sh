#!/bin/sh
set -eu

ip="${LAN_IP:-192.168.31.9}"
mkdir -p /etc/nginx/certs

if [ ! -f /etc/nginx/certs/server.crt ] || [ ! -f /etc/nginx/certs/server.key ]; then
  cat > /tmp/voicebridge-cert.cnf <<EOF
[req]
distinguished_name = req_distinguished_name
x509_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = ${ip}
[v3_req]
subjectAltName = IP:${ip}
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
EOF
  openssl req -x509 -nodes -newkey rsa:2048 -days 30 \
    -keyout /etc/nginx/certs/server.key \
    -out /etc/nginx/certs/server.crt \
    -config /tmp/voicebridge-cert.cnf
  chmod 600 /etc/nginx/certs/server.key
fi
