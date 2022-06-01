#!/bin/bash

set -euo pipefail

version=0.17.0

cd "$HOME"
scripts/wait-for-it.sh "trino.datalake.io:9081" -s -t 0 -- echo "JMX RMI is up"

cat << EOF > config.yaml
hostPort: trino.datalake.io:9080
ssl: false
EOF
wget -nv "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_httpserver/$version/jmx_prometheus_httpserver-$version.jar"
java -jar jmx_prometheus_httpserver-$version.jar 12345 config.yaml
