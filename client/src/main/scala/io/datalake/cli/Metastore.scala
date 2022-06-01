package io.datalake.cli

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hive.metastore.HiveMetaStoreClient
import org.apache.hadoop.hive.metastore.conf.MetastoreConf

import scala.collection.JavaConverters._

object Metastore {
  private val metastores = Map(
    "hive" -> "thrift://localhost:9083",
    "iceberg" -> "thrift://localhost:9084",
  )

  private def newClient(metastore: String): HiveMetaStoreClient = {
    val conf = new Configuration()
    MetastoreConf.setVar(conf, MetastoreConf.ConfVars.THRIFT_URIS, metastores(metastore))
    new HiveMetaStoreClient(conf)
  }

  def databases(args: List[String]): Unit = {
    val client = newClient(args.head)
    client.getAllDatabases.asScala.foreach(println)
  }

  def tables(args: List[String]): Unit = {
    val metastore :: dbName :: Nil = args
    val client = newClient(metastore)
    client.getAllTables(dbName).asScala.foreach(println)
  }

  def table(args: List[String]): Unit = {
    val metastore :: dbName :: tblName :: Nil = args
    val client = newClient(metastore)
    val table = client.getTable(dbName, tblName)
    println(table)
  }
}
