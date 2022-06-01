package io.datalake.lib

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.iceberg._
import org.apache.iceberg.catalog.{Namespace, TableIdentifier}
import org.apache.iceberg.data.parquet.GenericParquetReaders
import org.apache.iceberg.data.{GenericAppenderFactory, IcebergGenerics, Record}
import org.apache.iceberg.expressions.Expression
import org.apache.iceberg.hive.HiveCatalog
import org.apache.iceberg.io.{DataWriter, OutputFileFactory}
import org.apache.iceberg.parquet.ParquetUtil
import org.apache.parquet.hadoop.util.HadoopInputFile

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}
import java.util.UUID
import scala.collection.JavaConverters._

case class IcebergClient(name: String, uri: String, warehouse: String) {
  private val conf = new Configuration()

  private val catalog = {
    val catalog = new HiveCatalog
    catalog.setConf(conf)
    val props = Map(
      CatalogProperties.URI -> uri,
      CatalogProperties.WAREHOUSE_LOCATION -> warehouse,
    ).asJava
    catalog.initialize(name, props)
    catalog
  }

  private def newOutputFileFactory(table: Table): OutputFileFactory = {
    val now = Instant.now().atOffset(ZoneOffset.UTC)
    val partitionId = DateTimeFormatter.ofPattern("yyyyMMdd").format(now).toInt
    val taskId = System.currentTimeMillis()
    OutputFileFactory.builderFor(table, partitionId, taskId)
      .operationId(UUID.randomUUID().toString)
      .build()
  }

  def namespaces: Iterable[Namespace] = catalog.listNamespaces().asScala

  def tables(ns: String): Iterable[TableIdentifier] =
    catalog.listTables(Namespace.of(ns)).asScala

  def table(ns: String, tblName: String): Table = catalog.loadTable(TableIdentifier.of(ns, tblName))

  def read(table: Table): Iterable[Record] =
    IcebergGenerics.read(table).build().asScala

  def create(ns: String, tblName: String, schema: Schema, partitions: Iterable[String]): Table = {
    val partitionSpec = partitions
      .foldLeft(PartitionSpec.builderFor(schema))(_.identity(_))
      .build()
    val props = Map(
      TableProperties.FORMAT_VERSION -> "2",
    ).asJava
    catalog.createTable(TableIdentifier.of(ns, tblName), schema, partitionSpec, props)
  }

  /**
   * Append files to a table by partitioning records and rewriting files in Iceberg format.
   *
   * Input records are written to partition files according to the table's partition spec.
   * The partition files are then appended to the table.
   */
  def append(table: Table, files: Iterable[String]): Unit = {
    val outputFileFactory = newOutputFileFactory(table)
    val appenderFactory = new GenericAppenderFactory(table.schema(), table.spec())
    val partitionKey = new PartitionKey(table.spec(), table.schema())

    // Partition to writer mapping
    val writers = scala.collection.mutable.Map.empty[PartitionKey, DataWriter[Record]]

    for (file <- files) {
      val fileReader = ParquetFileUtil.reader(file)
      val schemaWithIds = ParquetFileUtil.alignSchema(fileReader, table)

      val recordReader = GenericParquetReaders.buildReader(table.schema(), schemaWithIds)
      var record: Record = null
      var i = 0
      while (i < fileReader.getRowGroups.size()) {
        val rowGroup = fileReader.readNextRowGroup()
        recordReader.setPageSource(rowGroup, 0)
        var j = 0
        while (j < rowGroup.getRowCount) {
          record = recordReader.read(record)
          // Get writer for the record partition
          partitionKey.partition(record)
          val writer = writers.getOrElseUpdate(
            partitionKey,
            appenderFactory.newDataWriter(
              outputFileFactory.newOutputFile(table.spec(), partitionKey),
              FileFormat.PARQUET,
              partitionKey.copy())
          )
          writer.write(record)
          j += 1
        }
        i += 1
      }
      fileReader.close()
    }
    writers.values.foreach(_.close())
    // Append partition files
    writers.values.map(_.toDataFile).foldLeft(table.newAppend())(_.appendFile(_)).commit()
  }

  /**
   * Append files to a table by rewriting files in Iceberg format.
   *
   * Each input file is expected to belong to a single partition.
   */
  def appendLazy(table: Table, files: Iterable[String]): Unit = {
    val outputFileFactory = newOutputFileFactory(table)
    files
      .map { file =>
        val fileReader = ParquetFileUtil.reader(file)
        val schemaWithIds = ParquetFileUtil.alignSchema(fileReader, table)
        val filePartition = ParquetFileUtil.filePartition(fileReader, table)

        val outputFile = outputFileFactory.newOutputFile(table.spec(), filePartition)
        val dst = outputFile.encryptingOutputFile().location()

        // Rewrite file with Iceberg schema
        val fileWriter = ParquetFileUtil.writer(dst, schemaWithIds)
        fileWriter.start()

        // Append row groups as is
        val inputStream = HadoopInputFile.fromPath(new Path(file), conf).newStream()
        fileWriter.appendRowGroups(inputStream, fileReader.getFooter.getBlocks, false)
        fileWriter.end(fileReader.getFileMetaData.getKeyValueMetaData)

        val metrics = ParquetUtil.footerMetrics(
          fileReader.getFooter, java.util.stream.Stream.empty(), MetricsConfig.forTable(table))
        fileReader.close()

        // Data file for the append operation
        DataFiles.builder(table.spec())
          .withEncryptedOutputFile(outputFile)
          .withMetrics(metrics)
          .withPartition(filePartition)
          .build()
      }
      .foldLeft(table.newAppend())(_.appendFile(_)).commit()
  }

  /**
   * Delete rows with an filter expression.
   *
   * This only works if the expression matches all rows in affected files, i.e. if the expression
   * matches partitions.
   */
  def delete(table: Table, expr: Expression): Unit =
    table.newDelete().deleteFromRowFilter(expr).commit()

  def expireSnapshots(table: Table): Unit =
    table
      .expireSnapshots()
      .expireOlderThan(System.currentTimeMillis())
      .retainLast(1)
      .commit()
}
