package io.datalake.cli

import io.datalake.lib.{IcebergClient, ParquetFileUtil}
import org.apache.iceberg.expressions.Expressions
import org.apache.iceberg.parquet.ParquetSchemaUtil

import scala.collection.JavaConverters._

object Iceberg {
  private lazy val iceberg = IcebergClient("iceberg", "thrift://localhost:9084", "s3a://datalake/iceberg")

  def namespaces(args: List[String]): Unit = iceberg.namespaces.foreach(println)

  def tables(args: List[String]): Unit = iceberg.tables(args.head).foreach(println)

  def table(args: List[String]): Unit = {
    val ns :: tblName :: Nil = args
    val table = iceberg.table(ns, tblName)
    println(table.name())
    println("History:")
    table.history().asScala.foreach(println)
    println("Location:")
    println(table.location())
    println("Partition spec:")
    println(table.spec())
    println("Properties:")
    table.properties().asScala.foreach(println)
    println("Snapshots:")
    table.snapshots().asScala.foreach(println)
  }

  def read(args: List[String]): Unit = {
    val ns :: tblName :: Nil = args
    val table = iceberg.table(ns, tblName)
    val records = iceberg.read(table)
    records.foreach(println)
    println(s"${records.size} rows")
  }

  def create(args: List[String]): Unit = {
    val ns :: tblName :: file :: Nil = args
    val fileSchema = ParquetFileUtil.schema(file)
    val tableSchema = ParquetSchemaUtil.convert(fileSchema)
    val table = iceberg.create(ns, tblName, tableSchema, Nil)
    iceberg.append(table, List(file))
  }

  def createPartitioned(args: List[String]): Unit = {
    val ns :: tblName :: partitions :: file :: Nil = args
    val fileSchema = ParquetFileUtil.schema(file)
    val tableSchema = ParquetSchemaUtil.convert(fileSchema)
    val table = iceberg.create(ns, tblName, tableSchema, partitions.split(','))
    iceberg.append(table, List(file))
  }

  def append(args: List[String]): Unit = {
    val ns :: tblName :: file :: Nil = args
    val table = iceberg.table(ns, tblName)
    iceberg.append(table, List(file))
  }

  def appendLazy(args: List[String]): Unit = {
    val ns :: tblName :: file :: Nil = args
    val table = iceberg.table(ns, tblName)
    iceberg.appendLazy(table, List(file))
  }

  def delete(args: List[String]): Unit = {
    val ns :: tblName :: key :: value :: Nil = args
    val table = iceberg.table(ns, tblName)
    iceberg.delete(table, Expressions.equal(key, value))
  }

  def expireSnapshots(args: List[String]): Unit = {
    val ns :: tblName :: Nil = args
    val table = iceberg.table(ns, tblName)
    iceberg.expireSnapshots(table)
  }
}
