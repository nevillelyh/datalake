package io.datalake.cli

import io.datalake.lib.{AppendColumn, AppendColumns}

object File {
  def appendColumn(args: List[String]): Unit = {
    val in :: out :: colName :: colType :: colValue :: Nil = args
    val column = colType.toLowerCase match {
      case "int" | "int32" | "integer" => AppendColumn(colName, colValue.toInt)
      case "long" | "int64" | "bigint" => AppendColumn(colName, colValue.toLong)
      case "string" | "varchar" => AppendColumn(colName, colValue)
      case _ => throw new IllegalArgumentException(s"Unsupported type: $colType")
    }
    AppendColumns(in, out, List(column)).run()
  }
}
