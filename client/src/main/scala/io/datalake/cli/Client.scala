package io.datalake.cli

case class Service(name: String, commands: List[Command])

case class Command(name: String, argNames: List[String], fn: List[String] => Unit)

object Client {
  private val services = List(
    Service("iceberg", List(
      Command("namespaces", Nil, Iceberg.namespaces),
      Command("tables", List("namespace"), Iceberg.tables),
      Command("table", List("namespace", "table"), Iceberg.table),
      Command("read", List("namespace", "table"), Iceberg.read),
      Command("create", List("namespace", "table", "file"), Iceberg.create),
      Command("create-partitioned", List("namespace", "table", "partitions", "file"), Iceberg.createPartitioned),
      Command("append", List("namespace", "table", "file"), Iceberg.append),
      Command("append-lazy", List("namespace", "table", "file"), Iceberg.appendLazy),
      Command("delete-partition", List("namespace", "table", "key", "value"), Iceberg.delete),
      Command("expire-snapshots", List("namespace", "table"), Iceberg.expireSnapshots),
    )),
    Service("metastore", List(
      Command("databases", List("hive|iceberg"), Metastore.databases),
      Command("tables", List("hive|iceberg", "database"), Metastore.tables),
      Command("table", List("hive|iceberg", "database", "table"), Metastore.table),
    )),
    Service("trino", List(
      Command("execute", List("query"), Trino.execute),
    )),
    Service("file", List(
      Command("append-column", List("in", "out", "name", "type", "value"), File.appendColumn),
    ))
  )

  private def help: Unit = {
    println("Usage: client <service> <command> [arg]...")
    for (service <- services) {
      println(s"    ${service.name}")
      for (cmd <- service.commands) {
        println(s"        ${cmd.name} ${cmd.argNames.map(a => s"<$a>").mkString(" ")}")
      }
    }
  }

  def main(args: Array[String]): Unit = {
    args.toList match {
      case sName :: cName :: cmdArgs =>
        services.find(_.name == sName) match {
          case None => help
          case Some(service) => service.commands.find(_.name == cName) match {
            case None => help
            case Some(command) =>
              if (cmdArgs.size == command.argNames.size) {
                command.fn(cmdArgs)
              } else {
                help
              }
          }
        }
      case _ =>
        help
    }
  }
}
