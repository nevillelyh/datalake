#!/bin/bash

set -euo pipefail

cd "$HOME"
scripts/wait-for-it.sh "minio.datalake.io:9000" -s -t 0 -- echo "MinIO is up"

mc alias set minio http://minio.datalake.io:9000 minioadmin minioadmin
if ! mc ls minio/datalake &> /dev/null; then
    mc mb minio/datalake
    mc admin user add minio datalake-key datalake-secret
    mc admin policy set minio readwrite user=datalake-key
fi
