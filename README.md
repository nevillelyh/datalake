datalake
========

Playground for Datalake projects.

# Usage

```
# Build Trino pugin first
cd plugin
./gradlew jar

# Set UID/GID environment variables
source env.sh

# Start everything
docker compose up

# Stop everything
docker compose down

# Reset everything
./bin/reset-all
```

# Service
- minio - MinIO for S3 emulation
- mysql - MySQL for Hive Metastore, Iceberg Metastore, and Trino catalog
- metastore-hive - Hive Metastore
- metastore-iceberg - Iceberg Metastore
- elastic - Elasticsearch
- scylla - Scylla
- trino - Trino

# Scripts

```
# Attach to service containers
bin/<service> bash

# Service command line interfaces
bin/<service> cli

# Load data
data/iris-load

# Query data
data/iris-query

# Check files
bin/aws s3 ls s3://datalake
bin/aws s3 sync --delete s3://datalake s3
```

# Data

- data/cli - mounted on /tmp/cli inside the containers
- data/minio

# Ports

- 9000: MinIO API
- 9001: MinIO console
- 3306: mysql
- 8080: Trino Web UI, HTTP
- 8443: Trino Web UI, HTTPS
- 9042: Scylla CQL native transport
- 9080: Trino JMX RMI registry
- 9081: Trino JMX RMI server
- 9200: Elasticsearch HTTP
- 9200: Elasticsearch transport
- 12345: JMX exporter

# Authentication

- MinIO:
    - user: minioadmin, password: minioadmin
    - AWS access key: datalake-key, AWS secret key: datalake-secret
- mysql
    - user: root, password: mysqlpass
    - database: hive, user: hive, password: hivepass
    - database: iceberg, user: iceberg, password: icebergpass
    - database: iris, user: trino, password: trinopass
- elastic:
    - user: elastic, password: elasticpass
- Trino
    - user: trino, password: trinopass
    - DBeaver - Driver properties
        - SSL = true
        - SSLVerification = NONE

# Known Issues

- MinIO container volume might have permission issue, add `-u MY_UID` to the `useradd` command in `minio/Dockerfile` so that the guest user ID matches that of the host user
- Elasticsearch requires a higher `max_map_count` on Linux, `sysctl -w vm.max_map_count=262144`
