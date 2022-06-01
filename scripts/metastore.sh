#!/bin/bash

set -euo pipefail

cd "$HOME"
scripts/wait-for-it.sh "mysql.datalake.io:3306" -s -t 0 -- echo "MySQL is up"
hive/bin/schematool -dbType mysql -validate || hive/bin/schematool -dbType mysql -initSchema
hive/bin/hive --service metastore
