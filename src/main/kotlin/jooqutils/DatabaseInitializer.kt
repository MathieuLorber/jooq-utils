package jooqutils

import com.google.common.io.ByteStreams
import jooqutils.util.DatasourcePool
import jooqutils.util.ShellRunner
import jooqutils.util.StatementExecutor
import mu.KotlinLogging
import org.jooq.Table
import org.jooq.impl.DependenciesParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Statement

object DatabaseInitializer {

    private val logger = KotlinLogging.logger {}

    // TODO should be useless - jooq does it
    fun dump(conf: DatabaseConfiguration): ShellRunner.CommandResult {
        val dump = when (conf.driver) {
            DatabaseConfiguration.Driver.psql ->
                // TODO export PGPASSWORD="$put_here_the_password"
                // -h bteiwyharfrwixoev1pr-postgresql.services.clever-cloud.com -p 5551 -U uyugupggbagqpqk0afsb
                ShellRunner.run(
                    "PGPASSWORD=${conf.password}",
                    "&&",
                    "pg_dump",
                    "-h",
                    conf.host,
                    "-p",
                    conf.port.toString(),
                    "-U",
                    conf.user,
                    "-d",
                    conf.databaseName,
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
        }
        logger.debug { "Dump" }
        if (logger.isDebugEnabled) {
            dump.lines.forEach {
                logger.debug { it }
            }
        }
        return dump
    }

    fun createDb(conf: DatabaseConfiguration) =
        when (conf.driver) {
            DatabaseConfiguration.Driver.psql -> ShellRunner.run("createdb", conf.databaseName)
            DatabaseConfiguration.Driver.mysql -> DatasourcePool.get(conf.copy(databaseName = "")).connection
                .createStatement()
                .use { statement ->
                    statement.execute("create database if not exists `${conf.databaseName}`")
                }
        }.let { Unit /* force exhaustive when() */ }

    fun dropDb(conf: DatabaseConfiguration): Unit =
        when (conf.driver) {
            DatabaseConfiguration.Driver.psql -> ShellRunner.run("dropdb", conf.databaseName)
            DatabaseConfiguration.Driver.mysql -> DatasourcePool.get(conf.copy(databaseName = "")).connection
                .createStatement()
                .use { statement ->
                    statement.execute("drop database if exists `${conf.databaseName}`;")
                }
        }.let { Unit /* force exhaustive when() */ }

    fun initializeSchema(conf: DatabaseConfiguration, sqlFilesPath: Path, sqlResultFile: Path?) {
        val sqlQueries = listSqlFiles(sqlFilesPath.toFile())
            .map { file ->
                ByteStreams
                    .toByteArray(file.inputStream())
                    .toString(Charsets.UTF_8)
                    .let { SqlQueryString(file.toPath(), it) }
            }
        val dependenciesSet = DependenciesParser.getDependenciesSet(sqlQueries, conf)
        val sb = if (sqlResultFile != null) StringBuilder() else null
        DatasourcePool.get(conf).connection.createStatement().use { statement ->
            execute(conf.driver, dependenciesSet, emptySet(), statement, sb)
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

    // TODO should fail if user try to insert ?
    private fun execute(
        driver: DatabaseConfiguration.Driver,
        dependenciesList: Set<DependenciesParser.QueryDependencies>,
        alreadyCreated: Set<Table<*>>,
        statement: Statement,
        sb: StringBuilder?
    ) {
        val createTables = dependenciesList
            .filter { (it.references.tables - alreadyCreated).isEmpty() }
        logger.debug { "Create tables from files ${createTables.map { it.query.filePath }.filterNotNull()}" }
        logger.debug { "Create tables ${createTables.flatMap { it.tables.map { it.name } }}" }
        createTables.forEach {
            sb?.appendLine(it.query.sql)
            sb?.appendLine()
            when (driver) {
                DatabaseConfiguration.Driver.psql -> StatementExecutor.execute(statement, it.query.sql)
                DatabaseConfiguration.Driver.mysql -> {
                    it.query.sql.split(";")
                        .map { it.trim() }
                        .filter { it != "" }
                        .forEach {
                            statement.execute(it)
                        }
                }
            }.let { Unit }

        }
        val remainingTables = dependenciesList - createTables
        val created = createTables.flatMap { it.tables }
        if (remainingTables.isNotEmpty()) {
            execute(driver, remainingTables, alreadyCreated + created, statement, sb)
        }
    }

    fun insert(conf: DatabaseConfiguration, sqlFilesPath: Path) {
        if (!sqlFilesPath.toFile().exists()) {
            // TODO log ?
            return
        }
        val sqlQueries = listSqlFiles(sqlFilesPath.toFile())
            .map { file ->
                ByteStreams
                    .toByteArray(file.inputStream())
                    .toString(Charsets.UTF_8)
                    .let { SqlQueryString(file.toPath(), it) }
            }
        DatasourcePool.get(conf).connection.createStatement().use { statement ->
            sqlQueries.forEach {
                statement.execute(it.sql)
            }
        }
    }
}