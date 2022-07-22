#!/bin/bash

set -euo pipefail

cd "$HOME"
scripts/wait-for-it.sh "crdb1.datalake.io:26257" -s -t 0 -- echo "CockroachDB is up"

/cockroach/cockroach init --insecure --host crdb1.datalake.io
/cockroach/cockroach sql --insecure --host crdb1.datalake.io
/cockroach/cockroach workload init tpcc postgresql://root@crdb1.datalake.io:26257?sslmode=disable
