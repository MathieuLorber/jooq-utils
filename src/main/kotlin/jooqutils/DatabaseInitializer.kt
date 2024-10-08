package jooqutils

import com.google.common.io.ByteStreams
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Statement
import jooqutils.util.DatasourcePool
import jooqutils.util.ShellRunner
import jooqutils.util.StatementExecutor
import mu.KotlinLogging
import org.jooq.Table
import org.jooq.impl.DependenciesParser

object DatabaseInitializer {

    private val logger = KotlinLogging.logger {}

    fun dump(conf: DatabaseConfiguration): ShellRunner.CommandResult {
        logger.debug { "Dump starts" }
        val dump =
            when (conf.driver) {
                DatabaseConfiguration.Driver.psql ->
                    ShellRunner.run(
                        "PGPASSWORD=${conf.password}",
                        "pg_dump",
                        "-h",
                        conf.host,
                        "-p",
                        conf.port.toString(),
                        "-U",
                        conf.user,
                        "-d",
                        conf.databaseName,
                        "-n",
                        conf.schemas.joinToString(separator = " "),
                        "--schema-only"
                    )

                DatabaseConfiguration.Driver.mysql ->
                    ShellRunner.run(
                        "mysqldump",
                        "--no-data",
                        "--user",
                        conf.user,
                        "--password=" + conf.password,
                        conf.databaseName
                    )

                DatabaseConfiguration.Driver.sqlite ->
                    ShellRunner.run(
                        "sqlite3",
                        conf.databaseName,
                        ".dump"
                    )
            }
        logger.debug { "Dump ok" }
        if (logger.isDebugEnabled) {
            dump.lines.forEach { logger.debug { it } }
        }
        return dump
    }

    fun createDb(conf: DatabaseConfiguration) =
        when (conf.driver) {
            DatabaseConfiguration.Driver.psql ->
                ShellRunner.run("createdb", conf.databaseName)

            DatabaseConfiguration.Driver.mysql ->
                DatasourcePool.get(conf.copy(databaseName = "")).connection.use {
                    it.createStatement().use { statement ->
                        statement.execute("create database if not exists `${conf.databaseName}`")
                    }
                }

            DatabaseConfiguration.Driver.sqlite -> {
                // nothing to do, a datasource connection creates the file
            }
        }

    fun dropDb(conf: DatabaseConfiguration) {
        when (conf.driver) {
            DatabaseConfiguration.Driver.psql ->
                ShellRunner.run("dropdb", conf.databaseName)

            DatabaseConfiguration.Driver.mysql ->
                DatasourcePool.get(conf.copy(databaseName = "")).connection.use {
                    it.createStatement().use { statement ->
                        statement.execute("drop database if exists `${conf.databaseName}`")
                    }
                }

            DatabaseConfiguration.Driver.sqlite ->
                ShellRunner.run("trash", conf.databaseName)
        }
    }

    fun initializeSchema(conf: DatabaseConfiguration, sqlFilesPath: Path, sqlResultFile: Path?) {
        val sqlQueries =
            listSqlFiles(sqlFilesPath.toFile()).map { file ->
                ByteStreams.toByteArray(file.inputStream()).toString(Charsets.UTF_8).let {
                    SqlQueryString(file.toPath(), it)
                }
            }
        val dependenciesSet = DependenciesParser.getDependenciesSet(sqlQueries, conf)
        val sb = if (sqlResultFile != null) StringBuilder() else null
        DatasourcePool.get(conf).connection.use {
            it.createStatement().use { statement ->
                when (conf.driver) {
                    DatabaseConfiguration.Driver.psql -> {
                        conf.schemas.forEach { schema ->
                            logger.debug { "create schema if not exists \"$schema\"" }
                            statement.execute("create schema if not exists \"$schema\"")
                        }
                    }

                    DatabaseConfiguration.Driver.mysql,
                    DatabaseConfiguration.Driver.sqlite -> {
                    }
                }
                execute(conf.driver, dependenciesSet, emptySet(), statement, sb)
            }
        }
        if (sqlResultFile != null) {
            sqlResultFile.toFile().parentFile.mkdirs()
            Files.write(sqlResultFile, sb.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun listSqlFiles(sqlFilesDir: File): List<File> =
        sqlFilesDir.listFiles().flatMap {
            if (it.isDirectory) {
                listSqlFiles(it)
            } else if (it.name.endsWith(".sql")) {
                logger.debug { "List file : ${it.name}" }
                listOf(it)
            } else {
                emptyList()
            }
        }

    private fun execute(
        driver: DatabaseConfiguration.Driver,
        dependenciesList: Set<DependenciesParser.QueryDependencies>,
        alreadyCreated: Set<Table<*>>,
        statement: Statement,
        sb: StringBuilder?
    ) {
        val createTables =
            dependenciesList.filter { (it.references.tables - alreadyCreated).isEmpty() }
        logger.debug {
            "Create tables from files ${createTables.map { it.query.filePath }.filterNotNull()}"
        }
        logger.debug { "Create tables ${createTables.flatMap { it.tables.map { it.name } }}" }
//        when (driver) {
//            DatabaseConfiguration.Driver.mysql,
//            DatabaseConfiguration.Driver.psql -> {
//            }
//
//            DatabaseConfiguration.Driver.sqlite -> statement.execute("BEGIN TRANSACTION;")
//        }
        createTables.forEach {
            sb?.appendLine(it.query.sql)
            sb?.appendLine()
            when (driver) {
                DatabaseConfiguration.Driver.psql ->
                    StatementExecutor.execute(statement, it.query.sql)

                DatabaseConfiguration.Driver.mysql,
                DatabaseConfiguration.Driver.sqlite ->
                    it.query.sql.split(";").map { it.trim() }.filter { it != "" }.forEach {
                        StatementExecutor.execute(statement, it)
                    }
            }
        }
        val remainingTables = dependenciesList - createTables
        val created = createTables.flatMap { it.tables }
        logger.debug { "Remaining tables ${remainingTables.flatMap { it.tables.map { it.name } }}" }
        if (createTables.isEmpty() && remainingTables.isNotEmpty()) {
            logger.error { "Dependencies impossible to resolve" }
            remainingTables.forEach {
                logger.info {
                    "${it.tables.map { it.name }} depends on ${it.references.tables.map { it.name }}"
                }
            }
            System.exit(1)
        }
        if (remainingTables.isNotEmpty()) {
            execute(driver, remainingTables, alreadyCreated + created, statement, sb)
        }
//        when (driver) {
//            DatabaseConfiguration.Driver.mysql,
//            DatabaseConfiguration.Driver.psql -> {
//            }
//
//            DatabaseConfiguration.Driver.sqlite ->
//                statement.execute("COMMIT;")
//        }
    }

    fun insert(conf: DatabaseConfiguration, sqlFilesPath: Path) {
        if (!sqlFilesPath.toFile().exists()) {
            return
        }
        val sqlQueries =
            listSqlFiles(sqlFilesPath.toFile()).sortedBy { it.name }.map { file ->
                file.name to
                        ByteStreams.toByteArray(file.inputStream()).toString(Charsets.UTF_8).let {
                            SqlQueryString(file.toPath(), it)
                        }
            }
        DatasourcePool.get(conf).connection.use {
            it.createStatement().use { statement ->
                sqlQueries.forEach { (filename, query) ->
                    logger.info { "Insert $filename" }
                    statement.execute(query.sql)
                }
            }
        }
    }
}
