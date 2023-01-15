package jooqutils

import java.nio.file.Path
import org.jooq.Table

data class SqlQueryString(val filePath: Path?, val sql: String)

data class TableReferences(val table: Table<*>, val references: References)

inline class References(val tables: Set<Table<*>>)

// TODO do class by type of db ? So we can specify default port for instance
// TODO jdbc url ? should be more logical for file dbs (sqlite)
data class DatabaseConfiguration(
    val driver: Driver,
    // TODO for the moment the library only handle localhost:5432
    val host: String,
    val port: String,
    // TODO doc : is the database file for sqlite
    val databaseName: String,
    val user: String,
    val password: String?,
    // TODO public is default for psql is emptySet() is provided here ?
    val schemas: Set<String>
) {
    enum class Driver {
        mysql,
        psql,
        sqlite
    }

    init {
        if (driver == Driver.mysql) {
            // TODO make it like sqlite will be simpler in code with withSchemata()
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
