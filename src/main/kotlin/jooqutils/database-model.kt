package jooqutils

import org.jooq.Table
import java.nio.file.Path

data class SqlQueryString(
    val filePath: Path?,
    val sql: String
)

data class TableReferences(val table: Table<*>, val references: References)

inline class References(val tables: Set<Table<*>>)

// TODO do class by type of db ? So we can specify default port for instance
data class DatabaseConfiguration(
    val driver: Driver,
    val databaseName: String,
    val user: String,
    val password: String,
    val schemas: Set<String>,
    val pgQuarrel: String?
) {
    enum class Driver {
        psql, mysql
    }

    init {
        if (driver == Driver.mysql) {
            require(schemas.isEmpty()) { "Mysql schemas list must be empty (database name == schema in Mysql" }
        }
    }
}