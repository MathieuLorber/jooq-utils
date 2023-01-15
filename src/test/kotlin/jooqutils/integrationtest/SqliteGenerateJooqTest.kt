package jooqutils.integrationtest

import java.nio.file.Paths
import jooqutils.DatabaseConfiguration
import jooqutils.DatabaseInitializer
import jooqutils.JooqGeneration
import org.junit.Test

class SqliteGenerateJooqTest {

    @Test
    fun `test generate ktts-webapp-template Jooq files`() {
        val userDir = Paths.get(System.getProperty("user.dir"))
        println(userDir.toAbsolutePath())
        val sqlFilesPath = userDir.resolve("src/test/resources/ktts-webapp-template-sqlite/schema")
        println(sqlFilesPath.toAbsolutePath())
        val conf =
            DatabaseConfiguration(
                DatabaseConfiguration.Driver.sqlite,
                "",
                "",
                "$userDir/target/test-sqlite.db",
                "sa",
                "sa",
                emptySet(),
                pgQuarrel = null
            )
        try {
            DatabaseInitializer.dropDb(conf)
            DatabaseInitializer.createDb(conf)
            DatabaseInitializer.initializeSchema(conf, sqlFilesPath, null)
            JooqGeneration.generateJooq(
                conf = conf,
                excludeTables = setOf("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES"),
                generatedPackageName = "lite.jooq",
                generatedCodePath = userDir.resolve("target/sqlite-generated-for-test")
            )
        } finally {
            DatabaseInitializer.dropDb(conf)
        }
    }
}
