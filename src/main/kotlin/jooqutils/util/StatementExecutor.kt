package jooqutils.util

import mu.KotlinLogging
import java.sql.Statement

object StatementExecutor {

    private val logger = KotlinLogging.logger {}

    fun execute(statement: Statement, request: String) {
        try {
            statement.execute(request)
        } catch (e: Exception) {
            logger.debug { "Exception while executing SQL request $request" }
            throw e
        }
    }

}