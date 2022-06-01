ThisBuild / scalaVersion     := "2.12.16"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "me.lyh"

lazy val root = (project in file("."))
  .settings(
    name := "datalake-client",
    libraryDependencies ++= Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % "1.12.264",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.13.3",
      "io.trino" % "trino-jdbc" % "390",
      "org.apache.hadoop" % "hadoop-aws" % "3.3.3",
      "org.apache.hadoop" % "hadoop-client" % "3.3.3",
      "org.apache.hive" % "hive-metastore" % "3.1.3",
      "org.apache.iceberg" % "iceberg-data" % "0.14.0",
      "org.apache.iceberg" % "iceberg-hive-metastore" % "0.14.0",
      "org.apache.iceberg" % "iceberg-parquet" % "0.14.0",
      "org.apache.iceberg" %% "iceberg-spark-3.3" % "0.14.0",
      "org.apache.iceberg" %% "iceberg-spark-runtime-3.3" % "0.14.0",
      "org.apache.parquet" % "parquet-hadoop" % "1.12.3",
      "org.apache.spark" %% "spark-core" % "3.3.0",
      "org.apache.spark" %% "spark-sql" % "3.3.0",
    ),
    excludeDependencies ++= Seq(
      "org.apache.parquet" % "parquet-hadoop-bundle"
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
