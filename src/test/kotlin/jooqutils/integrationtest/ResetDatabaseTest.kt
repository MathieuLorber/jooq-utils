package jooqutils.integrationtest

import java.nio.file.Paths
import jooqutils.DatabaseCleaner
import jooqutils.DatabaseConfiguration
import jooqutils.DatabaseInitializer
import org.junit.Ignore
import org.junit.Test

class ResetDatabaseTest {

    @Test
    @Ignore
    fun `test reset Orgarif Mysql database`() {
        val sqlFilesPath =
            Paths.get("${System.getProperty("user.dir")}/src/test/resources/orgarif-mysql")
        val conf =
            DatabaseConfiguration(
                DatabaseConfiguration.Driver.mysql,
                "localhost",
                5432,
                "dbtooling-orgarif-test",
                "root",
                "",
                setOf("public"),
                "/usr/local/bin",
                null)
        DatabaseInitializer.createDb(conf)
        DatabaseCleaner.clean(conf, null)
        DatabaseInitializer.initializeSchema(conf, sqlFilesPath, null)
    }

    @Test
    fun `test reset Orgarif Psql database`() {
        val sqlFilesPath =
            Paths.get("${System.getProperty("user.dir")}/src/test/resources/orgarif-psql")
        val conf =
            DatabaseConfiguration(
                DatabaseConfiguration.Driver.psql,
                "localhost",
                5432,
                "dbtooling-orgarif-test",
                "mlo",
                "",
                setOf("public"),
                "/usr/local/bin",
                null)
        DatabaseInitializer.createDb(conf)
        DatabaseCleaner.clean(conf, null)
        DatabaseInitializer.initializeSchema(conf, sqlFilesPath, null)
    }
}
