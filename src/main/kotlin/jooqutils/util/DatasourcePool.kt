package jooqutils.util

import javax.sql.DataSource
import jooqutils.DatabaseConfiguration

object DatasourcePool {

    private val map by lazy { mutableMapOf<DatabaseConfiguration, DataSource>() }

    fun get(configuration: DatabaseConfiguration) = synchronized(this) {
        map.get(configuration)
            ?: let {
                val datasource = createDatasource(configuration)
                map[configuration] = datasource
                datasource
            }
    }

    private fun createDatasource(configuration: DatabaseConfiguration): DataSource {
        val className =
            when (configuration.driver) {

                DatabaseConfiguration.Driver.psql ->
                    "org.postgresql.ds.PGSimpleDataSource"

                DatabaseConfiguration.Driver.mysql -> "com.mysql.cj.jdbc.MysqlDataSource"
                DatabaseConfiguration.Driver.sqlite -> "org.sqlite.SQLiteDataSource"
            }
        val clazz = Class.forName(className) as Class<DataSource>
        val datasource = clazz.getDeclaredConstructor().newInstance()
        when (configuration.driver) {
            DatabaseConfiguration.Driver.psql -> {
                invokeMethod(clazz, datasource, "setServerNames", arrayOf(configuration.host))
                invokeMethod(clazz, datasource, "setPortNumbers", intArrayOf(configuration.port.toInt()))
                invokeMethod(clazz, datasource, "setUser", configuration.user)
                invokeMethod(clazz, datasource, "setPassword", configuration.password ?: "")
            }

            DatabaseConfiguration.Driver.mysql -> {
                invokeMethod(clazz, datasource, "setServerName", configuration.host)
                invokeMethod(clazz, datasource, "setPortNumber", configuration.port)
                invokeMethod(clazz, datasource, "setUser", configuration.user)
                invokeMethod(clazz, datasource, "setPassword", configuration.password ?: "")
            }

            DatabaseConfiguration.Driver.sqlite -> {
                invokeMethod(clazz, datasource, "setUrl", "jdbc:sqlite:${configuration.databaseName}")
            }
        }
        invokeMethod(clazz, datasource, "setDatabaseName", configuration.databaseName)

        return datasource
    }

    private fun invokeMethod(
        clazz: Class<DataSource>,
        instance: DataSource,
        methodName: String,
        vararg params: Any
    ) {
        val method =
            clazz.methods.find { it.name == methodName }
                ?: throw IllegalArgumentException("method $methodName unknown")
        method.let { it.invoke(instance, *params) }
    }
}
