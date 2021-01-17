package org.jooq.impl

import jooqutils.DatabaseCleaner.simplifyQuery
import jooqutils.DatabaseConfiguration
import jooqutils.References
import jooqutils.SqlQueryString
import jooqutils.util.DatasourcePool
import mu.KotlinLogging
import org.jooq.SQLDialect
import org.jooq.Table

object DependenciesParser {

    private val logger = KotlinLogging.logger {}

    fun jooqDialect(driver: DatabaseConfiguration.Driver) = when (driver) {
        DatabaseConfiguration.Driver.psql -> SQLDialect.POSTGRES
        DatabaseConfiguration.Driver.mysql -> SQLDialect.MYSQL
    }

    data class QueryDependencies(
        val query: SqlQueryString,
        val tables: Set<Table<*>>,
        val references: References
    )

    fun getDependenciesSet(sqlQueries: List<SqlQueryString>, conf: DatabaseConfiguration): Set<QueryDependencies> {
        val jooqDsl = DSL.using(DatasourcePool.get(conf), jooqDialect(conf.driver))
        val dependenciesSet: Set<QueryDependencies> = sqlQueries
            .map { SqlQueryString(it.filePath, simplifyQuery(it.sql)) }
            .map { sqlQuery ->
                val queries = jooqDsl.parser().parse(sqlQuery.sql).queries().toList()
                val tables: Set<Table<*>> = queries
                    .map { query ->
                        when (query) {
                            is CreateTableImpl -> query.`$table`()
                            else -> null
                        }
                    }
                    .filterNotNull()
                    .toSet()
                val references: Set<Table<*>> = queries
                    .flatMap { query ->
                        when (query) {
                            is CreateTableImpl -> query.`$constraints`()
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
                            is AlterTableImpl -> query.`$addConstraint`().let {
                                when (it) {
                                    is ConstraintImpl ->
                                        listOf(it.`$referencesTable`()).filterNotNull()
                                    else -> {
                                        // TODO do something smarter
                                        logger.debug { it }
                                        emptyList()
                                    }
                                }
                            }
                            is CreateSequenceImpl,
                                // TODO permit constraints too !
                                // see dump
                            is CreateIndexImpl -> emptyList()
                            // is Delete/Insert/Update
                            is AbstractDelegatingRowCountQuery<*> -> emptyList()
                            else -> throw NotImplementedError(query.javaClass.name)
                        }
                    }
                    .toSet()
                // we doesn't need to know that a table references itself
                val filteredReferences = references - tables
                QueryDependencies(sqlQuery, tables, References(filteredReferences))
            }.toSet()

        // check no cyclic reference
        run {
            val map: Map<Table<*>, References> = dependenciesSet
                .flatMap { dependencies ->
                    dependencies.tables.map { it to dependencies.references }
                }
                .toMap()
            map.keys.forEach {
                checkReferences(it, map)
            }
        }

        return dependenciesSet
    }

    private fun checkReferences(
        startTable: Table<*>,
        map: Map<Table<*>, References>,
        tableChain: List<Table<*>> = emptyList()
    ) {
        val checkTable = (tableChain.lastOrNull() ?: startTable)
        val references = map[checkTable]
        if (references == null) {
            val badTable = (tableChain.dropLast(1).lastOrNull() ?: startTable)
            throw IllegalStateException("Table $badTable references $checkTable which doesn't exist.")
        }
        references.tables.forEach { table ->
            if (table == startTable) {
                val chain = tableChain.map { it.name }.joinToString(" -> ")
                throw IllegalStateException("Cyclic reference ${startTable.name} -> $chain -> ${table.name}")
            }
            // TODO is useless actually - check
            // checkReferences(startTable, map, tableChain + table)
        }
    }

}