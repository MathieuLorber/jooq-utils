package jooqutils

import java.nio.file.Path
import org.jooq.Table

data class SqlQueryString(val filePath: Path?, val sql: String)

data class TableReferences(val table: Table<*>, val references: References)

inline class References(val tables: Set<Table<*>>)

// TODO do class by type of db ? So we can specify default port for instance
data class DatabaseConfiguration(
    val driver: Driver,
    // TODO for the moment the library only handle localhost:5432
    val host: String,
    val port: Int,
    val databaseName: String,
    val user: String,
    val password: String?,
    // TODO public is default for psql is emptySet() is provided here ?
    val schemas: Set<String>,
    val executablesPath: String?,
    val pgQuarrel: String?
) {
    enum class Driver {
        psql,
        mysql
    }

    init {
        if (driver == Driver.mysql) {
            require(schemas.isEmpty()) {
                "Mysql schemas list must be empty (database name == schema in Mysql"
            }
        }
        if (driver == Driver.psql) {
            require(executablesPath != null) { "Psql requires non null executablesPath" }
        }
    }
}
