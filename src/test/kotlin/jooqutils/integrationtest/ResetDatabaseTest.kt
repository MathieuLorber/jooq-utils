package jooqutils.integrationtest

import jooqutils.DatabaseCleaner
import jooqutils.DatabaseConfiguration
import jooqutils.DatabaseInitializer
import org.junit.Test
import java.nio.file.Paths

class ResetDatabaseTest {

    @Test
    fun `test reset Orgarif Mysql database`() {
        val sqlFilesPath = Paths.get("${System.getProperty("user.dir")}/src/test/resources/orgarif")
        val conf = DatabaseConfiguration(
            DatabaseConfiguration.Driver.mysql,
            "localhost",
            5432,
            "dbtooling-orgarif-test",
            "root",
            "",
            emptySet(),
            null
        )
        DatabaseInitializer.createDb(conf)
        DatabaseCleaner.clean(conf, null)
        DatabaseInitializer.initializeSchema(conf, sqlFilesPath, null)
    }


}