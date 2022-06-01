#!/bin/bash

set -euo pipefail

dir="$(dirname "$(readlink -f "$0")")"

# HTTPS certificate
keytool -genkey -keyalg RSA -alias trino -storepass trinopass -keystore "$dir/keystore.jks" -validity 3650

# Password file
docker run -it --rm httpd:latest /usr/local/apache2/bin/htpasswd -nbB -C 10 trino trinopass > "$dir/password.db"
