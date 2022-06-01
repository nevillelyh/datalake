package io.datalake.lib

import org.apache.avro.Schema
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.parquet.bytes.{BytesInput, DirectByteBufferAllocator}
import org.apache.parquet.column.ParquetProperties
import org.apache.parquet.column.page.DictionaryPage
import org.apache.parquet.column.statistics.Statistics
import org.apache.parquet.column.values.ValuesWriter
import org.apache.parquet.hadoop.CodecFactory.BytesCompressor
import org.apache.parquet.hadoop.util.HadoopInputFile
import org.apache.parquet.hadoop.{CodecFactory, ParquetFileReader, ParquetFileWriter}
import org.apache.parquet.io.api.Binary
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.{LogicalTypeAnnotation, MessageType, PrimitiveType, Types}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

sealed trait ColumnType[T] {
  val avroType: Schema.Type
  val parquetType: PrimitiveTypeName
  val logicalType: LogicalTypeAnnotation = null
  val writeFn: (ValuesWriter, T) => Unit
  val statsFn: (Statistics[_], T) => Unit
}

object ColumnType {
  private def columnType[T](at: Schema.Type,
                            pt: PrimitiveTypeName,
                            wFn: (ValuesWriter, T) => Unit,
                            sFn: (Statistics[_], T) => Unit): ColumnType[T] =
    new ColumnType[T] {
      override val avroType: Schema.Type = at
      override val parquetType: PrimitiveTypeName = pt
      override val writeFn: (ValuesWriter, T) => Unit = wFn
      override val statsFn: (Statistics[_], T) => Unit = sFn
    }

  implicit val intType: ColumnType[Int] =
    columnType(Schema.Type.INT, PrimitiveTypeName.INT32, _.writeInteger(_), _.updateStats(_))
  implicit val longType: ColumnType[Long] =
    columnType(Schema.Type.LONG, PrimitiveTypeName.INT64, _.writeLong(_), _.updateStats(_))
  implicit val stringType: ColumnType[String] = new ColumnType[String] {
    override val avroType: Schema.Type = Schema.Type.STRING
    override val parquetType: PrimitiveTypeName = PrimitiveTypeName.BINARY
    override val logicalType: LogicalTypeAnnotation = LogicalTypeAnnotation.stringType()
    override val writeFn: (ValuesWriter, String) => Unit =
      (w, v) => w.writeBytes(Binary.fromString(v))
    override val statsFn: (Statistics[_], String) => Unit =
      (s, v) => s.updateStats(Binary.fromString(v))
  }
}

case class AppendColumn[T: ColumnType](name: String, value: T) {
  private val ct = implicitly[ColumnType[T]]

  def avro: Schema.Field = new Schema.Field(
    name,
    Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(ct.avroType)),
    null,
    null
  )

  def parquet: PrimitiveType =
    Option(ct.logicalType).foldLeft(Types.optional(ct.parquetType))(_.as(_)).named(name)

  def write(writer: ValuesWriter): Unit = ct.writeFn(writer, value)

  def updateStats(stats: Statistics[_]): Unit = ct.statsFn(stats, value)
}

/**
 * Append columns to a Parquet file.
 *
 * Existing columns are copied as is.
 * New columns are appended at the end as top-level optional columns.
 * The same value is appended for every row.
 */
case class AppendColumns(input: String, output: String, columns: Iterable[AppendColumn[_]]) {
  private val logger = LoggerFactory.getLogger(AppendColumns.getClass)

  private val ParquetAvroSchemaKey = "parquet.avro.schema"

  private def writerAvroSchema(fileReader: ParquetFileReader): Option[Schema] = {
    fileReader.getFileMetaData.getKeyValueMetaData
      .asScala
      .get(ParquetAvroSchemaKey)
      .map { schemaString =>
        val schema = new Schema.Parser().parse(schemaString)
        val fields = schema.getFields.asScala.map { field =>
          // Copy fields to reset internal position
          new Schema.Field(field.name(), field.schema(), field.doc(), field.defaultVal())
        }
        Schema.createRecord(
          schema.getName,
          schema.getDoc,
          schema.getNamespace,
          false,
          (fields ++ columns.map(_.avro)).asJava
        )
      }
  }

  private def writerParquetSchema(fileReader: ParquetFileReader): MessageType = {
    val schema = fileReader.getFileMetaData.getSchema
    val fields = schema.getFields.asScala.toList
    Types.buildMessage()
      .addFields(fields ++ columns.map(_.parquet): _*)
      .named(schema.getName)
  }

  private def writerMetadata(fileReader: ParquetFileReader): Map[String, String] =
    fileReader.getFileMetaData.getKeyValueMetaData.asScala.toMap ++
      writerAvroSchema(fileReader).map(ParquetAvroSchemaKey -> _.toString).toMap

  private def writeDictionaryPage(fileWriter: ParquetFileWriter,
                                  valuesWriter: ValuesWriter,
                                  compressor: BytesCompressor): Unit = {
    val page = valuesWriter.toDictPageAndClose
    if (page != null) {
      // Compress and write dictionary page if exists
      val compressedPage = new DictionaryPage(
        compressor.compress(page.getBytes),
        page.getBytes.size().toInt,
        page.getDictionarySize,
        page.getEncoding)
      fileWriter.writeDictionaryPage(compressedPage)
    }
  }

  def run(): Unit = {
    val conf = new Configuration()
    val codecFactory = CodecFactory.createDirectCodecFactory(
      conf, DirectByteBufferAllocator.getInstance(), ParquetProperties.DEFAULT_PAGE_SIZE)
    val parquetProps = ParquetProperties.builder().build()

    val fileReader = ParquetFileUtil.reader(input)
    val inStream = HadoopInputFile.fromPath(new Path(input), conf).newStream()

    val writerSchema = writerParquetSchema(fileReader)
    val fileWriter = ParquetFileUtil.writer(output, writerSchema)

    fileWriter.start()

    fileReader.getRowGroups.asScala.foreach { block =>
      val rowCount = block.getRowCount
      fileWriter.startBlock(rowCount)

      val bfReader = fileReader.getBloomFilterDataReader(block)
      val columnMetas = block.getColumns.asScala
      val existingColumns = columnMetas zip fileReader.getFileMetaData.getSchema.getColumns.asScala
      // Copy existing columns as is
      existingColumns.foreach { case (metadata, descriptor) =>
        val bf = bfReader.readBloomFilter(metadata)
        val columnIdx = fileReader.readColumnIndex(metadata)
        val offsetIdx = fileReader.readOffsetIndex(metadata)
        fileWriter.appendColumnChunk(descriptor, inStream, metadata, bf, columnIdx, offsetIdx)
      }

      val codecs = columnMetas.map(_.getCodec).distinct
      if (codecs.size > 1) {
        logger.warn("Multiple codecs: {}", codecs)
      }
      val codec = codecs.last
      val compressor = codecFactory.getCompressor(codec)

      // Append new columns
      columns.foreach { column =>
        val descriptor = writerSchema.getColumnDescription(Array(column.name))
        fileWriter.startColumn(descriptor, rowCount, codec)

        val rlWriter = parquetProps.newRepetitionLevelWriter(descriptor)
        val dlWriter = parquetProps.newDefinitionLevelWriter(descriptor)
        val vWriter = parquetProps.newValuesWriter(descriptor)
        val stats = Statistics.createStats(column.parquet)
        (1 to rowCount.toInt).foreach { _ =>
          rlWriter.writeInteger(0) // Non-repeated field
          dlWriter.writeInteger(1) // Top-level optional field
          column.write(vWriter)
          column.updateStats(stats)
        }

        val rlBytes = rlWriter.getBytes
        val dlBytes = dlWriter.getBytes
        val vBytes = vWriter.getBytes
        writeDictionaryPage(fileWriter, rlWriter, compressor)
        writeDictionaryPage(fileWriter, dlWriter, compressor)
        writeDictionaryPage(fileWriter, vWriter, compressor)
        val bytes = BytesInput.concat(dlBytes, rlBytes, vBytes)

        fileWriter.writeDataPage(
          rowCount.toInt,
          bytes.size().toInt,
          compressor.compress(bytes),
          stats,
          rlWriter.getEncoding,
          dlWriter.getEncoding,
          vWriter.getEncoding,
          null,
          null
        )
        fileWriter.endColumn()
      }

      fileWriter.endBlock()
    }

    fileWriter.end(writerMetadata(fileReader).asJava)
  }
}