package io.datalake.spark

import io.datalake.lib.IcebergClient
import org.apache.iceberg.spark.actions.SparkActions
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

import scala.collection.JavaConverters._

case class Iris(sepal_length_cm: Float,
                sepal_width_cm: Float,
                petal_length_cm: Float,
                petal_width_cm: Float,
                species: String)

object IcebergSpark {
  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf()
      .set("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .set("spark.sql.catalog.iceberg", "org.apache.iceberg.spark.SparkCatalog")
      .set("spark.sql.catalog.iceberg.type", "hive")
      .set("spark.sql.catalog.iceberg.uri", "thrift://localhost:9084")
      .set("spark.sql.catalog.iceberg.warehouse", "s3a://datalake/iceberg")
      .set("spark.sql.legacy.createHiveTableByDefault", "false")

    val session = SparkSession
      .builder()
      .appName("IcebergAction")
      .master("local[4]")
      .config(sparkConf)
      .getOrCreate()

    session.table("iceberg.iris.iris").collect().foreach(println)

    val species = Array("setosa", "versicolor", "virginica")
    val data = (1 to 10).map(i => Iris(i, i, i, i, species(i % species.length)))
    val df = session.createDataFrame(data)
    df.show()
    df.writeTo("iceberg.iris.iris").append()

    val actions = SparkActions.get(session)
    val iceberg = IcebergClient("iceberg", "thrift://localhost:9084", "s3a://datalake/iceberg")

    val table = iceberg.table("iris", "iris")
    val rrRes = actions.rewriteDataFiles(table).execute().rewriteResults()
    val esRes = actions
      .expireSnapshots(table)
      .expireOlderThan(System.currentTimeMillis())
      .retainLast(1)
      .execute()
    val doRes = actions
      .deleteOrphanFiles(table)
      .olderThan(System.currentTimeMillis())
      .execute()

    session.close()

    println("Rewrite data files result:")
    rrRes.asScala.foreach { r =>
      println(
        r.info().partition(),
        r.info().globalIndex(),
        r.info().partitionIndex(),
        r.addedDataFilesCount(),
        r.rewrittenDataFilesCount())
    }
    println("Expire snapshots result:")
    println(
      esRes.deletedDataFilesCount(),
      esRes.deletedManifestsCount(),
      esRes.deletedManifestListsCount())
    println("Delete orphan files result:")
    doRes.orphanFileLocations().asScala.foreach(println)
  }
}
