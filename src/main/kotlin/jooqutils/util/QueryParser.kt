package jooqutils.util

import java.util.concurrent.atomic.AtomicBoolean
import jooqutils.DatabaseConfiguration
import org.jooq.impl.DependenciesParser
import jooqutils.References
import jooqutils.SqlQueryString
import jooqutils.TableReferences
import mu.KotlinLogging
import org.jooq.Table
import org.jooq.impl.AlterTableImpl
import org.jooq.impl.ConstraintImpl
import org.jooq.impl.CreateIndexImpl
import org.jooq.impl.CreateTableImpl
import org.jooq.impl.DSL
import org.jooq.impl.ParserException

object QueryParser {

    private val logger = KotlinLogging.logger {}

    data class Constraint(val constraintName: String, val table: Table<*>)

    data class Queries(
        val index: List<String>,
        val constraints: List<Constraint>,
        val tables: List<TableReferences>
    )

    // TODO shitty signature obvsly
    fun classifyQueries(queries: List<SqlQueryString>, driver: DatabaseConfiguration.Driver): Queries {
        val parsingError = AtomicBoolean(false)
        val jooqQueries =
            queries.flatMap {
                try {
                    val parse = DSL.using(DependenciesParser.jooqDialect(driver)).parser().parse(it.sql)
                    parse.queries().toList()
                } catch (e: ParserException) {
                    logger.error { "Jooq parsing exception : ${it.sql}" }
                    parsingError.set(true)
                    emptyList()
                }
            }
        if (parsingError.get()) {
            throw RuntimeException("One or multiple Jooq parsing exception.")
        }
        val index = jooqQueries.filterIsInstance<CreateIndexImpl>().mapNotNull { it.`$index`()?.name }
        val constraints =
            jooqQueries
                .filterIsInstance<AlterTableImpl>()
                .map {
                    val c = it.`$addConstraint`()
                    when (c) {
                        is ConstraintImpl -> {
                            if (c.`$referencesTable`() != null) {
                                Constraint(c.name, it.`$table`())
                            } else {
                                null
                            }
                        }

                        else -> {
                            // TODO do something smarter
                            logger.debug { it }
                            throw RuntimeException()
                        }
                    }
                }
                .filterNotNull()
        val tables =
            jooqQueries.filterIsInstance<CreateTableImpl>().map {
                val constraints =
                    it.`$constraints`()
                        .map { constraint ->
                            when (constraint) {
                                is ConstraintImpl -> constraint.`$referencesTable`()
                                else -> {
                                    // TODO do something smarter
                                    logger.debug { constraint }
                                    null
                                }
                            }
                        }
                        .filterNotNull()
                        .toSet()
                TableReferences(it.`$table`(), References(constraints))
            }
        return Queries(index, constraints, tables)
    }
}
