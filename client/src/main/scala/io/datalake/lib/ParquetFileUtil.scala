package io.datalake.lib

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.iceberg.data.GenericRecord
import org.apache.iceberg.parquet.ParquetSchemaUtil
import org.apache.iceberg.{PartitionKey, Table}
import org.apache.parquet.column.ParquetProperties
import org.apache.parquet.hadoop.ParquetFileWriter.Mode
import org.apache.parquet.hadoop.util.{HadoopInputFile, HadoopOutputFile}
import org.apache.parquet.hadoop.{ParquetFileReader, ParquetFileWriter, ParquetWriter}
import org.apache.parquet.schema.{GroupType, LogicalTypeAnnotation, MessageType, Types}

import scala.collection.JavaConverters._

object ParquetFileUtil {
  private lazy val conf = new Configuration()

  /** Align Parquet file schema with a target Iceberg table, i.e. with IDs. */
  def alignSchema(fileReader: ParquetFileReader, table: Table): MessageType = {
    val fileSchema = fileReader.getFileMetaData.getSchema

    // Use Parquet schema from table as source of truth
    // This version should have IDs for all fields and Iceberg compatible types
    val tableSchema = ParquetSchemaUtil.convert(table.schema(), fileSchema.getName)

    // Align types against file
    alignTypes(tableSchema, fileSchema)
  }

  private def alignTypes(table: MessageType, file: MessageType): MessageType = {
    val builder = Types.buildMessage()
    alignTypes(table.asGroupType(), file.asGroupType())
      .getFields
      .asScala
      .foreach(builder.addField)
    builder
      .as(table.getLogicalTypeAnnotation)
      .id(Option(table.getId).map(_.intValue()).getOrElse(0))
      .named(table.getName)
  }

  private def alignTypes(table: GroupType, file: GroupType): GroupType = {
    require(table.getRepetition == file.getRepetition)
    require(table.getName == file.getName)
    table.getFields
      .asScala
      .map { tf =>
        val ff = file.getFields.get(file.getFieldIndex(tf.getName))
        require(tf.getRepetition == ff.getRepetition)
        require(tf.isPrimitive == ff.isPrimitive)
        if (tf.isPrimitive) {
          val tpf = tf.asPrimitiveType()
          val fpf = ff.asPrimitiveType()
          if (tpf.getPrimitiveTypeName != fpf.getPrimitiveTypeName) {
            // Table and file schema type mismatch, use file schema type
            Types
              .primitive(fpf.getPrimitiveTypeName, tf.getRepetition)
              .as(fpf.getLogicalTypeAnnotation)
              .id(Option(tpf.getId).map(_.intValue()).getOrElse(0))
              .named(tpf.getName)
          } else {
            tf
          }
        } else {
          val tgf = tf.asGroupType()
          val fgf = ff.asGroupType()
          alignTypes(tgf, fgf)
        }
      }
      .foldLeft(Types.buildGroup(table.getRepetition))(_.addField(_))
      .as(table.getLogicalTypeAnnotation)
      .id(Option(table.getId).map(_.intValue()).getOrElse(0))
      .named(table.getName)
  }

  def schema(file: String): MessageType = {
    val r = reader(file)
    val schema = r.getFileMetaData.getSchema
    r.close()
    schema
  }

  def reader(file: String): ParquetFileReader = {
    val path = new Path(file)
    val inputFile = HadoopInputFile.fromPath(path, conf)
    ParquetFileReader.open(inputFile)
  }

  def writer(file: String, schema: MessageType): ParquetFileWriter = {
    val dstPath = new Path(file)
    val outputFile = HadoopOutputFile.fromPath(dstPath, conf)
    new ParquetFileWriter(
      outputFile,
      schema,
      Mode.CREATE,
      ParquetWriter.DEFAULT_BLOCK_SIZE,
      ParquetWriter.MAX_PADDING_SIZE_DEFAULT,
      ParquetProperties.DEFAULT_COLUMN_INDEX_TRUNCATE_LENGTH,
      ParquetProperties.DEFAULT_STATISTICS_TRUNCATE_LENGTH,
      ParquetProperties.DEFAULT_PAGE_WRITE_CHECKSUM_ENABLED)
  }

  /**
   * Get table partition from a Parquet file.
   *
   * All records in the file are expected to belong to the same partition.
   * This is verified by checking partition column's statistics, i.e. min == max && # nulls = 0
   */
  def filePartition(fileReader: ParquetFileReader, table: Table): PartitionKey = {
    val partitionFields = table.spec().fields().asScala.map(_.name()).toSet
    val partitionKey = new PartitionKey(table.spec(), table.schema())
    val partitions = fileReader.getFooter.getBlocks.asScala
      .map { block =>
        // Partition record for the block, i.e. a partition spec can have multiple fields
        val record = GenericRecord.create(table.spec().schema())
        block.getColumns.asScala.foreach { column =>
          val path = column.getPath.toArray
          // Current column is a partition key
          if (path.size == 1 && partitionFields.contains(path.head)) {
            val key = path.head
            val stats = column.getStatistics
            require(stats.getNumNulls == 0, s"Partition column $key contains NULL")
            val min = stats.getMinBytes
            val max = stats.getMaxBytes
            require(java.util.Arrays.equals(min, max), s"Partition column $key contains multiple values")
            if (column.getPrimitiveType.getLogicalTypeAnnotation == LogicalTypeAnnotation.stringType()) {
              record.setField(key, stats.minAsString())
            } else {
              record.setField(key, stats.genericGetMin())
            }
          }
        }
        val pk = partitionKey.copy()
        pk.partition(record)
        // Partition key for the block
        pk
      }
      .distinct
    // All blocks should have the same partition
    require(partitions.size == 1, s"File ${fileReader.getFile} contains multiple partitions")
    partitions.head
  }
}
