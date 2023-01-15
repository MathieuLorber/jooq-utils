package jooqutils

import com.google.common.hash.Hashing
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.temporal.ChronoField.DAY_OF_MONTH
import java.time.temporal.ChronoField.HOUR_OF_DAY
import java.time.temporal.ChronoField.MINUTE_OF_HOUR
import java.time.temporal.ChronoField.MONTH_OF_YEAR
import java.time.temporal.ChronoField.SECOND_OF_MINUTE
import java.time.temporal.ChronoField.YEAR
import jooqutils.jooq.JooqConfiguration
import jooqutils.jooq.JooqGeneratorStrategy
import jooqutils.util.ShellRunner
import mu.KotlinLogging
import org.jooq.codegen.GenerationTool

object JooqGeneration {

    private val logger = KotlinLogging.logger {}

    val formatter by lazy {
        DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .toFormatter()
    }

    val noHash = "NOHASH"

    fun generateJooq(
        conf: DatabaseConfiguration,
        excludeTables: Set<String> = emptySet(),
        generatedPackageName: String,
        generatedCodePath: Path
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
        if (!conf.password.isNullOrEmpty() || !diffConf.password.isNullOrEmpty()) {
            logger.error { "Can't handle passwords with pgquarrel yet" }
            return
        }
        val hashRunDatabase = dumpHash(conf)
        val hashGenerateDatabase = dumpHash(diffConf)
        if (hashRunDatabase != hashGenerateDatabase) {
            val commandResult =
                ShellRunner.run(
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
            val file =
                destinationPath.resolve(
                    "diff_" +
                            formatter.format(LocalDateTime.now()) +
                            "_" +
                            shortenHash(hashRunDatabase) +
                            "-" +
                            shortenHash(hashGenerateDatabase) +
                            ".sql"
                )
            logger.info { "Writing diff to $file" }
            file.toFile().parentFile.mkdirs()
            Files.write(file, diff.toByteArray(Charsets.UTF_8))
        } else {
            logger.info { "No diff between databases" }
        }
    }

    fun dumpHash(conf: DatabaseConfiguration): String {
        val dumpResult = DatabaseInitializer.dump(conf)
        if (dumpResult.lines.isEmpty()) {
            return noHash
        }
        val dump = dumpResult.lines.reduce { acc, s -> "$acc\n$s" }
        return Hashing.sha256().hashString(dump, Charsets.UTF_8).toString()
    }

    fun shortenHash(hash: String) = if (hash != noHash) hash.substring(0, 8) else hash
}
