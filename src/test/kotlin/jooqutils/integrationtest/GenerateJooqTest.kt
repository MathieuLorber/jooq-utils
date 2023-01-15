package jooqutils.integrationtest

import java.nio.file.Paths
import jooqutils.DatabaseConfiguration
import jooqutils.DatabaseInitializer
import jooqutils.JooqGeneration
import org.junit.Ignore
import org.junit.Test

class GenerateJooqTest {

    @Ignore
    @Test
    fun `test generate Orgarif Jooq files`() {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val sqlFilesPath = userDir.resolve("/src/test/resources/orgarif")
        val conf =
            DatabaseConfiguration(
                DatabaseConfiguration.Driver.mysql,
                "localhost",
                5432,
                "dbtooling-orgarif-test",
                "root",
                "",
                emptySet(),
                null
            )
        try {
            DatabaseInitializer.dropDb(conf)
            DatabaseInitializer.createDb(conf)
            DatabaseInitializer.initializeSchema(conf, sqlFilesPath, null)
            JooqGeneration.generateJooq(
                conf = conf,
                excludeTables = setOf("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES"),
                generatedPackageName = "lite.jooq",
                generatedCodePath = userDir.resolve("target/generated-for-test")
            )
        } finally {
            DatabaseInitializer.dropDb(conf)
        }
    }
}
