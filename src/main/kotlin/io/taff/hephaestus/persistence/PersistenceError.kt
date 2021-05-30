package io.taff.hephaestus.persistence

import org.jetbrains.exposed.sql.ResultRow

sealed class PersistenceError(msg: String, override val cause: Throwable? = null) : RuntimeException(msg, cause) {

    data class RowMappingError(val row: ResultRow?) : PersistenceError("Failed mapping row. row: $row")

    data class FieldToDbMappingError(val value: Any?,
                                     val dbType: String,
                                     val kotlinType: Class<*>?) : PersistenceError("Cannot persist $value as a $dbType because it is not a(n) $kotlinType")

    data class UnpersistedUpdateError(val model: Any) : PersistenceError("Cannot update $model because it hasn't been persisted yet")
}
