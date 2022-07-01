#!/bin/bash

set -euo pipefail

cd "$HOME"
scripts/wait-for-it.sh metastore-hive.datalake.io:9083 -s -t 0 -- echo "metastore-hive is up"
scripts/wait-for-it.sh metastore-iceberg.datalake.io:9083 -s -t 0 -- echo "metastore-iceberg is up"
scripts/wait-for-it.sh elastic.datalake.io:9200 -s -t 0 -- echo "elastic is up"
scripts/wait-for-it.sh scylla.datalake.io:9042 -s -t 0 -- echo "scylla is up"
/usr/lib/trino/bin/run-trino
