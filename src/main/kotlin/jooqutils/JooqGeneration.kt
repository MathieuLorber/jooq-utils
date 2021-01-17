package jooqutils

import com.google.common.hash.Hashing
import jooqutils.jooq.JooqConfiguration
import jooqutils.jooq.JooqGeneratorStrategy
import jooqutils.util.ShellRunner
import mu.KotlinLogging
import org.jooq.codegen.GenerationTool
import java.nio.file.Files
import java.nio.file.Paths

object JooqGeneration {

    private val logger = KotlinLogging.logger {}

    fun generateJooq(
        conf: DatabaseConfiguration,
        excludeTables: Set<String> = emptySet(),
        generatedPackageName: String,
        generatedCodePath: String
    ) {
        GenerationTool.generate(
            JooqConfiguration.generateConfiguration(
                conf = conf,
                excludeTables = excludeTables,
                generatedPackageName = generatedPackageName,
                generatedCodePath = generatedCodePath,
                generatorStrategyClass = JooqGeneratorStrategy::class
            )
        )
    }

    // TODO should take 2 conf
    fun generateDiff(conf: DatabaseConfiguration, diffDatabaseName: String) {
        if (conf.pgQuarrel == null) {
            // TODO logs
//            logger
            return
        }
        val hashRunDatabase = dumpHash(conf)
        val hashGenerateDatabase = dumpHash(conf.copy(databaseName = diffDatabaseName))
        if (hashRunDatabase != hashGenerateDatabase) {
            val commandResult = ShellRunner.run(
                conf.pgQuarrel,
                // TODO use conf ;)
                "--source-host=localhost",
                "--source-port=5432",
                "--source-dbname=${diffDatabaseName}",
                "--source-user=${System.getProperty("user.name")}",
                "--source-no-password",
                "--target-host=localhost",
                "--target-port=5432",
                "--target-dbname=${conf.databaseName}",
                "--target-user=${System.getProperty("user.name")}",
                "--target-no-password"
            )
            val diff = commandResult.lines.fold("") { acc, s -> acc + "\n" + s }
            val file = Paths.get(
                System.getProperty("user.dir"), "/jooq-lib/build/db-diff/diff-" +
                        hashRunDatabase.substring(0, 8) + "-" + hashGenerateDatabase.substring(0, 8) + ".sql"
            )
            logger.info { "Writing diff to $file" }
            file.toFile().parentFile.mkdirs()
            Files.write(file, diff.toByteArray(Charsets.UTF_8))
        }
    }

    fun dumpHash(conf: DatabaseConfiguration): String {
        val dumpResult = DatabaseInitializer.dump(conf)
        val dump = dumpResult.lines.reduce { acc, s -> "$acc\n$s" }
        return Hashing.sha256()
            .hashString(dump, Charsets.UTF_8)
            .toString()
    }
}