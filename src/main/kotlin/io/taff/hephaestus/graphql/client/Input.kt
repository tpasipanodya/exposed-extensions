package io.taff.hephaestus.graphql.client

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * An input for a graphql operation.
 */
data class Input(val name: String,
                 val required: Boolean,
                 val value: Any,
                 val type: String = value.javaClass.simpleName) {

    fun coalescedValue() : Any = if (value is OffsetDateTime) {
        value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    } else value
}
