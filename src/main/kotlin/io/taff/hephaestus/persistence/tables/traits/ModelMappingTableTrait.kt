package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.persistence.PersistenceError
import io.taff.hephaestus.persistence.models.Model
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider.createEntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.*

interface ModelMappingTableTrait<M : Model, T : UUIDTable> :  MappingTrait<M> {

    val id: Column<EntityID<UUID>>
    val createdAt: Column<OffsetDateTime>
    val updatedAt: Column<OffsetDateTime>
    fun self() : T

    /**
     * Transform a ResultRow to a fully initialized model with all attributes
     * from the result row set. This merges the model mapping logic you specified
     * with the table-specific base attribute mapping Hephaestus performs inside appendModelAttributes.
     *
     * Use this to transform your result rows into models.
     */
    fun toModel(row: ResultRow) = initializeModel(row)
        .also {
            it.id = row[id].value
            it.createdAt = row[createdAt]
            it.updatedAt = row[updatedAt]
        }

    /** Insert an ordered list of models */
    fun insert(vararg models: M) = self().batchInsert(models.toList()) { model ->
        appendStatementValues(this, model)
        appendBaseStatementValues(this, model)
    }.forEachIndexed { index, resultRow ->
        models[index].also { model -> model.id = resultRow[id].value }
    }.let { models }

    /**
     * Update an ordered list of models.
     */
    fun update(transaction: Transaction, vararg models: M) = models.also {
        BatchUpdateStatement(self()).apply {
            models.forEach { model ->
                model.id?.let { modelId ->
                    addBatch(createEntityID(modelId, self()))
                    appendStatementValues(this, model)
                    appendBaseStatementValues(this, model)
                    model.id?.let { safeId -> this[id] = createEntityID(safeId, self()) }
                } ?: throw PersistenceError.UnpersistedUpdateError(model)
            }
        }.execute(transaction)
    }

    fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M) {
        model.id?.let { safeId -> stmt[id] = createEntityID(safeId, self()) }
    }

    /** Delete a list of models */
    fun delete(vararg models: M) = models.also {
        val ids = models.mapNotNull { it.id }
        val idColumn = id
        transaction { self().deleteWhere { idColumn inList ids } }
    }

    /** populate insert/update statements */
}