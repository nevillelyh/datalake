package io.datalake.cli

import java.sql.{Connection, DriverManager}
import java.util.Properties

object Trino {
  private val url = "jdbc:trino://localhost:8443/"

  private def newConnection(): Connection = {
    DriverManager.registerDriver(new io.trino.jdbc.TrinoDriver)
    val props = new Properties()
    props.setProperty("user", "trino")
    props.setProperty("password", "trinopass")
    props.setProperty("SSL", "true")
    props.setProperty("SSLVerification", "NONE")
    DriverManager.getConnection(url, props)
  }

  private lazy val connection = newConnection()

  def execute(args: List[String]): Unit = {
    val sql = args.head
    val rs = connection.createStatement().executeQuery(sql)
    val meta = rs.getMetaData
    val nCols = meta.getColumnCount
    val width = Array(0) ++ (1 to nCols).map { i =>
      math.min(24, meta.getColumnDisplaySize(i))
    }
    val header = (1 to nCols)
      .map(i => s"%${width(i)}s".format(meta.getColumnName(i)))
      .mkString(" | ")
    val sep = (1 to nCols)
      .map(i => "-" * width(i))
      .mkString("-+-")
    println(header)
    println(sep)
    while (rs.next()) {
      val row = (1 to nCols)
        .map(i => s"%${width(i)}s".format(rs.getObject(i)))
        .mkString(" | ")
      println(row)
    }
  }
}
