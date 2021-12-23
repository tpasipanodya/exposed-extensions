package io.taff.exposed.extensions

import org.jetbrains.exposed.sql.ResultRow

/** Persistence errors speicific to this framework (not SQL) */
sealed class PersistenceError(msg: String, override val cause: Throwable? = null) : RuntimeException(msg, cause) {

    /** When a row cannot be mapped to a record */
    data class RowMappingError(val row: ResultRow?) : PersistenceError("Failed mapping row. row: $row")

    /** When a record cannot be mapped to a DB row */
    data class FieldToDbMappingError(val value: Any?,
                                     val dbType: String,
                                     val kotlinType: Class<*>?) : PersistenceError("Cannot persist $value as a $dbType because it is not a(n) $kotlinType")

    /** Raised when an attempt to update a record that hasn't been inserted yet is made */
    data class UnpersistedUpdateError(val record: Any) : PersistenceError("Cannot update $record because it hasn't been persisted yet")
}
