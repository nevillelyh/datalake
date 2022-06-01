-- Metastores
CREATE DATABASE hive;
CREATE DATABASE iceberg;

CREATE USER 'hive'@'%' IDENTIFIED BY 'hivepass';
CREATE USER 'iceberg'@'%' IDENTIFIED BY 'icebergpass';

GRANT ALL PRIVILEGES ON hive.* TO 'hive'@'%';
GRANT ALL PRIVILEGES ON iceberg.* TO 'iceberg'@'%';

-- MySQL catalog
CREATE DATABASE iris;
CREATE USER 'trino'@'%' IDENTIFIED BY 'trinopass';
GRANT ALL PRIVILEGES ON iris.* TO 'trino'@'%';
