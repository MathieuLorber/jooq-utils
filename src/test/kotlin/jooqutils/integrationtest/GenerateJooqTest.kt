package jooqutils.integrationtest

import jooqutils.DatabaseConfiguration
import jooqutils.DatabaseInitializer
import jooqutils.JooqGeneration
import org.junit.Test
import java.nio.file.Paths

class GenerateJooqTest {

    @Test
    fun `test generate Orgarif Jooq files`() {
        val sqlFilesPath = Paths.get("${System.getProperty("user.dir")}/src/test/resources/orgarif")
        val conf = DatabaseConfiguration(
            DatabaseConfiguration.Driver.mysql,
            "localhost",
            5432,
            "dbtooling-orgarif-test",
            "root",
            "",
            emptySet(),
            "/Users/mlo/git/pgquarrel/pgquarrel"
        )
        try {
            DatabaseInitializer.dropDb(conf)
            DatabaseInitializer.createDb(conf)
            DatabaseInitializer.initializeSchema(conf, sqlFilesPath, null)
            JooqGeneration.generateJooq(
                conf,
                setOf("SPRING_SESSION", "SPRING_SESSION_ATTRIBUTES"),
                "lite.jooq",
                "target/generated-for-test"
            )
        } finally {
            DatabaseInitializer.dropDb(conf)
        }
    }

}