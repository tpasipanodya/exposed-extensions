package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.persistence.PersistenceError.UnpersistedUpdateError
import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.postgres.moment
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider.createEntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime.now
import java.util.*

/**
 * A table that provides basic model mapping functionality.
 */
abstract class ModelMappingTable<M : Model>(open val name: String) : UUIDTable(name) {

    /** The created_at column */
    val createdAt = moment("created_at").clientDefault { now() }

    /** The updated_at column */
    val updatedAt = moment("updated_at").clientDefault { now() }

    /** create an entity id from an id */
    private fun entityId(id: UUID) = createEntityID(id, this)

    /** map a result row to a model */
    fun toModel(row: ResultRow) = fillModel(row, initializeModel(row))

    /** Initialize a model */
    abstract fun initializeModel(row: ResultRow) : M

    /** populate model from result row */
    internal open fun fillModel(row: ResultRow, model: M) = model.also {
        it.id = row[id].value
        it.createdAt = row[createdAt]
        it.updatedAt = row[updatedAt]
    }

    /** populate insert/update statements */
    protected open fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        model.id?.let { stmt[id] = entityId(it) }
    }

    /**
     * Insert an ordered list of models into the database and run the after insert callback.
     */
    fun insert(vararg models: M) = batchInsert(models.toList()) { fillStatement(this, it) }
        .forEachIndexed { index, resultRow ->
            models[index].also { model -> model.id = resultRow[id].value }
        }.let { models }

    /**
     * Update an ordered list of models and run the after update callbacks.
     */
    fun update(transaction: Transaction, vararg models: M) = models.also {
        BatchUpdateStatement(this).apply {
            models.forEach { model ->
                model.id?.let { id ->
                    addBatch(entityId(id))
                    fillStatement(this, model)
                } ?: throw UnpersistedUpdateError(model)
            }
        }.execute(transaction)
    }

    /**
     * Hard delete models.
     */
    open fun delete(vararg models: M) = models.also {
        val idColumn = id
        transaction {
            deleteWhere {
                idColumn inList models.mapNotNull { it.id }
            }
        }
    }
}

