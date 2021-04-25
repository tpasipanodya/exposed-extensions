package io.taff.hephaestus.persistence.postgres

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.postgresql.jdbc.PgArray

/**
 * Represents Postgres Array columns.
 */
sealed class Array(override var nullable: Boolean = false, open val parser: (json: String) -> Any) : IColumnType {

    /**
     * parse the value returned from the DB.
     */
    override fun valueFromDB(value: Any) = when (value) {
        is PgArray -> {
            (value.array as kotlin.Array<*>).let { values ->
                values.map { value ->
                    parser(value.toString())
                }
            }
        }
        else -> super.valueFromDB(value)
    }


    /**
     * Serialize a value for the DB.
     */
    override fun valueToDB(value: Any?) = if (value is Iterable<*>) {
        connection().createArrayOf(dataType, (value as List<*>?)?.toTypedArray())
    } else {
        super.valueToDB(value)
    }

    /**
     * serialize non-null values for the DB.
     */
    override fun notNullValueToDB(value: Any) = if (value is Iterable<*>) {
        connection().createArrayOf(sqlType(), (value as List<*>).toTypedArray())
    } else {
        super.notNullValueToDB(value)
    }

    private fun connection() = TransactionManager
            .currentOrNull()
            ?.connection
            ?.connection as java.sql.Connection

    abstract var dataType: String

    override fun sqlType() = "$dataType[]"
}

/**
 * Integer arrays.
 */
class IntArray(override var nullable: Boolean = false,
               override val parser: (json: String) -> Any) : Array(nullable, parser) {
    override var dataType = "integer"
}

/**
 * Long Arrays
 */
class LongArray(override var nullable: Boolean = false,
                override val parser: (json: String) -> Any) : Array(nullable, parser) {
    override var dataType = "bigint"
}

/**
 * String Arrays.
 */
class StringArray(override var nullable: Boolean = false, override val parser: (json: String) -> Any) : Array(nullable, parser) {
    override var dataType = "text"
}

/**
 * Create an integer array column.
 */
fun org.jetbrains.exposed.sql.Table.intArray(name: String):
        Column<List<Int>> = registerColumn(
        name,
        IntArray(parser = String::toInt)
)

/**
 * Create a long array column.
 */
fun org.jetbrains.exposed.sql.Table.longArray(name: String):
        Column<List<Long>> = registerColumn(
        name,
        LongArray(parser = String::toLong)
)

/**
 * Create a String array column.
 */
fun org.jetbrains.exposed.sql.Table.stringArray(name: String):
        Column<List<String>> = registerColumn(
        name,
        StringArray { value -> value }
)
