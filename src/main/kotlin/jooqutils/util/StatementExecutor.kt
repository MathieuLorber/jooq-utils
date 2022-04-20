package jooqutils.util

import java.sql.Statement
import mu.KotlinLogging

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
