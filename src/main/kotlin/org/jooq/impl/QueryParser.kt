package org.jooq.impl

import jooqutils.DatabaseConfiguration
import jooqutils.SqlQueryString
import jooqutils.util.DatasourcePool

object QueryParser {

    // TODO shitty signature obvsly
    fun classifyQueries(queries: List<SqlQueryString>, conf: DatabaseConfiguration):
            Triple<List<String>, List<Pair<String, String>>, List<String>> {
        val jooqDsl = DSL.using(DatasourcePool.get(conf), DependenciesParser.jooqDialect(conf.driver))
        val jooqQueries = queries.flatMap { jooqDsl.parser().parse(it.sql).queries().toList() }
        val indexQueries = jooqQueries.filterIsInstance<CreateIndexImpl>()
            .map { it.`$index`().name }
        val constraints = jooqQueries.filterIsInstance<AlterTableImpl>()
            .map {
                val c = it.`$addConstraint`()
                when (c) {
                    is ConstraintImpl -> {
                        if (c.`$referencesTable`() != null) {
                            it.`$table`().name to c.name
                        } else {
                            null
                        }
                    }
                    // TODO ?
                    else -> {
                        println(it)
                        throw RuntimeException()
                    }
                }
            }
            .filterNotNull()
        val createTableQueries = jooqQueries.filterIsInstance<CreateTableImpl>()
            .map { it.`$table`().name }
        return Triple(indexQueries, constraints, createTableQueries)
    }

}