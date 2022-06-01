#!/bin/bash

set -euo pipefail

cd "$HOME"
scripts/wait-for-it.sh "mysql.datalake.io:3306" -s -t 0 -- echo "MySQL is up"
mysql --host=mysql.datalake.io --user=root --password=mysqlpass < scripts/mysql-init.sql
