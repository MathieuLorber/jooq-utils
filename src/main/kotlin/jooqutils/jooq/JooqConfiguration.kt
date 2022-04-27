package jooqutils.jooq

import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import javax.annotation.Nonnull
import javax.annotation.Nullable
import jooqutils.DatabaseConfiguration
import kotlin.reflect.KClass
import org.jooq.codegen.GeneratorStrategy
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.ForcedType
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.SchemaMappingType
import org.jooq.meta.jaxb.Strategy
import org.jooq.meta.jaxb.Target
import org.jooq.meta.mysql.MySQLDatabase
import org.jooq.meta.postgres.PostgresDatabase

// TODO[db-tooling] use Config or parameters ?
object JooqConfiguration {
    // TODO[db-tooling] simplify now !
    fun generateConfiguration(
        conf: DatabaseConfiguration,
        excludeTables: Set<String>,
        // generatorClass: KClass<out org.jooq.codegen.Generator>? = null,
        generatedPackageName: String? = null,
        generatedCodePath: Path? = null,
        generatorStrategyClass: KClass<out GeneratorStrategy>? = null
    ) =
        Configuration()
            .withJdbc(
                Jdbc()
                    .withDriver(
                        when (conf.driver) {
                            // TODO centralize org.postgresql.Driver & com.mysql.cj.jdbc.Driver
                            DatabaseConfiguration.Driver.psql -> "org.postgresql.Driver"
                            DatabaseConfiguration.Driver.mysql -> "com.mysql.cj.jdbc.Driver"
                        })
                    .withUrl(
                        when (conf.driver) {
                            DatabaseConfiguration.Driver.psql ->
                                "jdbc:postgresql://${conf.host}/${conf.databaseName}"
                            DatabaseConfiguration.Driver.mysql ->
                                "jdbc:mysql://${conf.host}/${conf.databaseName}?serverTimezone=UTC"
                        })
                    .withUser(conf.user)
                    .withPassword(conf.password))
            .withGenerator(
                Generator()
                    .withDatabase(
                        Database()
                            .withName(
                                when (conf.driver) {
                                    DatabaseConfiguration.Driver.psql ->
                                        PostgresDatabase::class.java.name
                                    DatabaseConfiguration.Driver.mysql ->
                                        MySQLDatabase::class.java.name
                                })
                            .withIncludes(".*")
                            .withExcludes(excludeTables.joinToString(separator = "|"))
                            .apply {
                                when (conf.driver) {
                                    DatabaseConfiguration.Driver.psql -> {
                                        if (conf.schemas.isNotEmpty()) {
                                            withSchemata(
                                                conf.schemas.map {
                                                    SchemaMappingType().withInputSchema(it)
                                                })
                                        } else {}
                                    }
                                    DatabaseConfiguration.Driver.mysql -> {
                                        withSchemata(
                                            SchemaMappingType().withInputSchema(conf.databaseName))
                                    }
                                }.let { Unit }
                            }
                            .apply {
                                val timeStampForcedType =
                                    ForcedType().apply {
                                        userType = Instant::class.java.name
                                        includeTypes = "TIMESTAMP"
                                        converter = TimestampToInstantConverter::class.java.name
                                    }
                                val forcedTypes =
                                    when (conf.driver) {
                                        DatabaseConfiguration.Driver.psql -> {
                                            val timestampWithTimeZoneForcedType =
                                                ForcedType().apply {
                                                    userType = Instant::class.java.name
                                                    includeTypes = "TIMESTAMP\\ WITH\\ TIME\\ ZONE"
                                                    converter =
                                                        TimestampWithTimeZoneToInstantConverter::class
                                                            .java
                                                            .name
                                                }
                                            listOf(
                                                timeStampForcedType,
                                                timestampWithTimeZoneForcedType)
                                        }
                                        DatabaseConfiguration.Driver.mysql -> {
                                            val booleanForcedType =
                                                ForcedType().apply {
                                                    name = "BOOLEAN"
                                                    includeTypes = "(?i:TINYINT\\(1\\))"
                                                }
                                            val uuidForcedType =
                                                ForcedType().apply {
                                                    userType = UUID::class.java.name
                                                    includeTypes = "CHAR\\(32\\)"
                                                    includeExpression = ".*\\.*id\$"
                                                    converter = CharToUUIDConverter::class.java.name
                                                }
                                            listOf(
                                                timeStampForcedType,
                                                booleanForcedType,
                                                uuidForcedType)
                                        }
                                    }
                                withForcedTypes(forcedTypes)
                            })
                    .apply {
                        if (generatorStrategyClass != null) {
                            withStrategy(Strategy().withName(generatorStrategyClass.java.name))
                        }
                    }
                    .apply {
                        if (generatedPackageName != null || generatedCodePath != null) {
                            if (generatedPackageName == null || generatedCodePath == null) {
                                throw IllegalArgumentException(
                                    "generatedPackageName and generatedCodePath must be both null or not null")
                            }
                            withTarget(
                                Target()
                                    .withPackageName("$generatedPackageName.generated")
                                    .withDirectory(generatedCodePath.toFile().absolutePath))
                        }
                    }
                    .withGenerate(
                        Generate().apply {
                            isNullableAnnotation = true
                            nullableAnnotationType = Nullable::class.java.name
                            isNonnullAnnotation = true
                            nonnullAnnotationType = Nonnull::class.java.name
                        }))
}
