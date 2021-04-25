package io.taff.hephaestus.persistence.postgres

import io.taff.hephaestus.persistence.PersistenceError
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Table

class MomentColumnType(override var nullable: Boolean = false) : IColumnType {

    override fun sqlType() = "TIMESTAMPTZ"

    override fun valueToString(value: Any?): String = when (value) {
        null -> "NULL"

        is OffsetDateTime -> format(value)

        else -> nonNullValueToString(value)
    }

    override fun valueFromDB(value: Any): Any {
        val instant = (value as Timestamp).toInstant()

        return OffsetDateTime.ofInstant(instant, ZoneId.systemDefault())
    }

    override fun notNullValueToDB(value: Any) = when (value) {
        is OffsetDateTime -> Timestamp(value.toInstant().toEpochMilli())

        else -> throw PersistenceError.FieldToDbMappingError(
            value,
            "TIMESTAMPTZ",
            OffsetDateTime::class.java
        )
    }

    fun format(moment: OffsetDateTime) = moment.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

fun Table.moment(name: String): Column<OffsetDateTime> = registerColumn(name, MomentColumnType())
