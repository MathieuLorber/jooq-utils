package jooqutils

import jooqutils.util.DatasourcePool
import mu.KotlinLogging
import org.jooq.Table
import org.jooq.impl.QueryParser
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Statement

object DatabaseCleaner {

    private val logger = KotlinLogging.logger {}

    fun clean(conf: DatabaseConfiguration, sqlResultFile: Path?) {
        val commandResult = DatabaseInitializer.dump(conf)
        val fullDump = commandResult.lines
            // replace comments by end of lines
            .map {
                if (it.startsWith("--")) {
                    "\n"
                } else {
                    simplifyQuery(it)
                }
            }
            .joinToString(separator = "\n")
        val queries = fullDump.split("\n\n")
        val filteredQueries = queries
            .map { it.trim() }
            .let {
                when (conf.driver) {
                    DatabaseConfiguration.Driver.psql -> {
                        it
                            .filter { !it.startsWith("SET statement_timeout") }
                            .filter { !it.startsWith("SELECT pg_catalog.set_config") }
                            .filter { !it.startsWith("ALTER SEQUENCE") }
                            // TODO or no reference !!
                            .filter { !it.startsWith("CREATE UNIQUE INDEX") }
                            .filter { "ADD GENERATED BY DEFAULT AS IDENTITY" !in it }
                            .filter { "SET DEFAULT nextval" !in it }
                            .filter { "REVOKE ALL" !in it }
                    }
                    DatabaseConfiguration.Driver.mysql -> {
                        it
                            .filter { "SET DEFAULT nextval" !in it }
                    }
                }
            }
            .filter { it.trim() != "" }
            .map { SqlQueryString(null, it) }
        // TODO index vs constraint
        val classified = QueryParser.classifyQueries(filteredQueries, conf)
        val sb = if (sqlResultFile != null) StringBuilder() else null
        DatasourcePool.get(conf).connection.createStatement().use { statement ->
            classified.index.forEach { index ->
                val sql = "drop index if exists ${index}"
                logger.debug { "Execute \"$sql\"" }
                statement.execute(sql)
                sb?.appendLine(sql + ";")
                sb?.appendLine()
            }
            classified.constraints.forEach { c ->
                val sql = "alter table ${c.table.name} drop constraint ${c.constraintName}"
                logger.debug { "Execute \"$sql\"" }
                statement.execute(sql)
                sb?.appendLine(sql + ";")
                sb?.appendLine()
            }
            val reverseDependencies = getReverseDependencies(classified.tables)
            dropTables(reverseDependencies, emptySet(), statement, sb)
        }
        if (sqlResultFile != null) {
            sqlResultFile.toFile().parentFile.mkdirs()
            Files.write(sqlResultFile, sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun getReverseDependencies(tables: List<TableReferences>): Map<Table<*>, References> {
        val reversed = tables
            .flatMap { references ->
                references.references.tables.map { it to references.table }
            }
            .groupBy { it.first }
            .mapValues { References(it.value.map { it.second }.toSet()) }
        // tables with no dependencies disappear in the process
        val missing = tables.map { it.table }
            .let { it - reversed.keys }
            .map { it to References(emptySet()) }
        return reversed + missing
    }

    private fun dropTables(
        reverseDependencies: Map<Table<*>, References>,
        alreadyDroped: Set<Table<*>>,
        statement: Statement,
        sb: StringBuilder?
    ) {
        val dropTables = reverseDependencies
            .entries
            .filter { (it.value.tables - alreadyDroped).isEmpty() }
            .map { it.key }
            .toSet()
        dropTables.forEach {
            logger.debug { "Drop table ${it.name} (if exists)" }
            val sql = "drop table if exists ${it.name}"
            statement.execute(sql)
            sb?.appendLine(sql + ";")
            sb?.appendLine()
        }
        val remainingRelations = reverseDependencies.filter { it.key !in dropTables }
        if (remainingRelations.isNotEmpty()) {
            dropTables(remainingRelations, alreadyDroped + dropTables, statement, sb)
        }
    }

    // TODO because it seems Jooq doesn't like "VARCHAR(255)" for primary keys =s
    fun simplifyQuery(it: String) = it.replace("VARCHAR", "CHARACTER VARYING")
}