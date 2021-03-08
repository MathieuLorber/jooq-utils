package jooqutils

import com.google.common.hash.Hashing
import jooqutils.jooq.JooqConfiguration
import jooqutils.jooq.JooqGeneratorStrategy
import jooqutils.util.ShellRunner
import mu.KotlinLogging
import org.jooq.codegen.GenerationTool
import java.nio.file.Files
import java.nio.file.Path
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

    fun generateDiff(
        conf: DatabaseConfiguration,
        diffConf: DatabaseConfiguration,
        destinationPath: Path
    ) {
        if (conf.pgQuarrel == null) {
            logger.error { "No pgquarrel configured" }
            return
        }
        // TODO
        if (!conf.password.isEmpty() || !diffConf.password.isEmpty()) {
            logger.error { "Can't handle passwords with pgquarrel yet" }
            return
        }
        val hashRunDatabase = dumpHash(conf)
        val hashGenerateDatabase = dumpHash(diffConf)
        if (hashRunDatabase != hashGenerateDatabase) {
            val commandResult = ShellRunner.run(
                conf.pgQuarrel,
                "--source-host=${diffConf.host}",
                "--source-port=${diffConf.port}",
                "--source-dbname=${diffConf.databaseName}",
                "--source-user=${diffConf.user}",
                "--source-no-password",
                "--target-host=${conf.host}",
                "--target-port=${conf.port}",
                "--target-dbname=${conf.databaseName}",
                "--target-user=${conf.user}",
                "--target-no-password"
            )
            val diff = commandResult.lines.fold("") { acc, s -> acc + "\n" + s }
            val file = destinationPath.resolve(
                "diff-" + hashRunDatabase.substring(0, 8) + "-" + hashGenerateDatabase.substring(0, 8) + ".sql"
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