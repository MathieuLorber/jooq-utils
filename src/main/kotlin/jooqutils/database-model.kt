package jooqutils

import java.nio.file.Path
import org.jooq.Table

data class SqlQueryString(val filePath: Path?, val sql: String)

data class TableReferences(val table: Table<*>, val references: References)

inline class References(val tables: Set<Table<*>>)

data class DatabaseConfiguration(
    val driver: Driver,
    val host: String,
    val port: String,
    val databaseName: String,
    val user: String,
    val password: String?,
    val schemas: Set<String>
) {
    enum class Driver {
        mysql,
        psql,
        sqlite
    }

    init {
        if (driver == Driver.mysql) {
            require(schemas.isEmpty()) {
                "Mysql schemas list must be empty (database name == schema in Mysql)"
            }
        }
        if (driver == Driver.sqlite) {
            require(schemas.isEmpty()) {
                "SQLITE schemas list must be empty"
            }
//            require(schemas == setOf("public")) {
//                "SQLITE schemas list must be [\"public\"]"
//            }
        }
    }
}
