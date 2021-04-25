package io.taff.hephaestus.persistence.postgres

import io.taff.hephaestus.Config
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject

/**
 * Represnts Postgres JsonB columns.
 */
class Jsonb(override var nullable: Boolean = false, val parser: (json: String) -> Any) : IColumnType {

    override fun sqlType() = "jsonb"

    override fun nonNullValueToString(value: Any) = "'${Config.objectMapper.writeValueAsString(value)}'"

    override fun notNullValueToDB(value: Any): String = Config.objectMapper.writeValueAsString(value)

    override fun valueFromDB(value: Any) = when (value) {
        is PGobject -> { parser(value.value) }
        else -> value
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        PGobject().also {
            it.type = "jsonb"
            it.value = value as String
            stmt[index] = it
        }
    }
}

/**
 * Create a JsonB column.
 */
fun <T : Any> org.jetbrains.exposed.sql.Table.jsonb(name: String,
                                                    parser: (json: String) -> T) : Column<T> = registerColumn(name, Jsonb(parser = parser))
