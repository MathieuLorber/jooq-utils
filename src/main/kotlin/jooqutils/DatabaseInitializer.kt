package jooqutils

import com.google.common.io.ByteStreams
import jooqutils.util.DatasourcePool
import jooqutils.util.ShellRunner
import mu.KotlinLogging
import org.jooq.Table
import org.jooq.impl.DependenciesParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Statement

object DatabaseInitializer {

    private val logger = KotlinLogging.logger {}

    // TODO should be useless - jooq does it
    fun dump(conf: DatabaseConfiguration) = when (conf.driver) {
        DatabaseConfiguration.Driver.psql ->
            // TODO export PGPASSWORD="$put_here_the_password"
            ShellRunner.run("pg_dump", "-s", conf.databaseName, "--schema-only")
        DatabaseConfiguration.Driver.mysql ->
            ShellRunner.run("mysqldump", "--no-data", "-u", conf.user, "--password=" + conf.password, conf.databaseName)
    }

    fun createDb(conf: DatabaseConfiguration) =
        when (conf.driver) {
            DatabaseConfiguration.Driver.psql -> ShellRunner.run("createdb", conf.databaseName)
            DatabaseConfiguration.Driver.mysql -> DatasourcePool.get(conf).connection.createStatement()
                .use { statement ->
                    statement.execute("create database if not exists `${conf.databaseName}`")
                }
        }.let { Unit /* force exhaustive when() */ }

    fun dropDb(conf: DatabaseConfiguration): Unit =
        when (conf.driver) {
            DatabaseConfiguration.Driver.psql -> ShellRunner.run("dropdb", conf.databaseName)
            DatabaseConfiguration.Driver.mysql -> DatasourcePool.get(conf).connection.createStatement()
                .use { statement ->
                    statement.execute("drop database if exists `${conf.databaseName}`;")
                }
        }.let { Unit /* force exhaustive when() */ }

    fun initialize(conf: DatabaseConfiguration, sqlFilesPath: Path) {
        val sqlQueries = listSqlFiles(sqlFilesPath.toFile())
            .map { file ->
                ByteStreams
                    .toByteArray(file.inputStream())
                    .toString(Charsets.UTF_8)
                    .let { SqlQueryString(file.toPath(), it) }
            }
        val dependenciesSet = DependenciesParser.getDependenciesSet(sqlQueries, conf)
        val sb = StringBuilder()
        DatasourcePool.get(conf).connection.createStatement().use { statement ->
            createTables(dependenciesSet, emptySet(), statement, sb)
        }
        // TODO dir must be a parameter
//        val createTablesFile = Paths.get(System.getProperty("user.dir"), "/jooq-lib/build/db/create-tables.sql")
//        createTablesFile.toFile().parentFile.mkdirs()
//        Files.write(createTablesFile, sb.toString().toByteArray(Charsets.UTF_8))
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
    private fun createTables(
        dependenciesList: Set<DependenciesParser.QueryDependencies>,
        alreadyCreated: Set<Table<*>>,
        statement: Statement,
        sb: StringBuilder
    ) {
        val createTables = dependenciesList
            .filter { (it.references.tables - alreadyCreated).isEmpty() }
        logger.debug { "Create tables from files ${createTables.map { it.query.filePath }.filterNotNull()}" }
        logger.debug { "Create tables ${createTables.flatMap { it.tables.map { it.name } }}" }
        createTables.forEach {
            statement.execute(it.query.sql)
            sb.appendLine(it.query.sql + ";")
            sb.appendLine()
        }
        val remainingTables = dependenciesList - createTables
        val created = createTables.flatMap { it.tables }
        if (remainingTables.isNotEmpty()) {
            createTables(remainingTables, alreadyCreated + created, statement, sb)
        }
    }

    fun insert(conf: DatabaseConfiguration, sqlFilesPath: Path) {
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