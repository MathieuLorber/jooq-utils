package org.jooq.impl

import jooqutils.DatabaseConfiguration
import jooqutils.References
import jooqutils.SqlQueryString
import jooqutils.TableReferences
import jooqutils.util.DatasourcePool
import mu.KotlinLogging
import org.jooq.Table

object QueryParser {

    private val logger = KotlinLogging.logger {}

    data class Constraint(val constraintName: String, val table: Table<*>)

    data class Queries(
        val index: List<String>,
        val constraints: List<Constraint>,
        val tables: List<TableReferences>
    )

    // TODO shitty signature obvsly
    fun classifyQueries(queries: List<SqlQueryString>, conf: DatabaseConfiguration): Queries {
        val jooqDsl = DSL.using(DatasourcePool.get(conf), DependenciesParser.jooqDialect(conf.driver))
        val jooqQueries = queries.flatMap { jooqDsl.parser().parse(it.sql).queries().toList() }
        val index = jooqQueries.filterIsInstance<CreateIndexImpl>().map { it.`$index`().name }
        val constraints = jooqQueries.filterIsInstance<AlterTableImpl>()
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
        val tables = jooqQueries.filterIsInstance<CreateTableImpl>()
            .map {
                val constraints = it.`$constraints`()
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