package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.PersistenceError
import io.taff.exposed.extensions.models.Model
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider.createEntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Model mapping behavior applicable to tables.
 *
 * @param ID The model's concrete id type.
 * @param M The model's conrete type.
 */
interface ModelMappingTableTrait<ID : Comparable<ID>, M : Model<ID>, T : IdTable<ID>> {

    val id: Column<EntityID<ID>>
    val createdAt: Column<Instant>
    val updatedAt: Column<Instant>
    fun self() : T

    fun initializeModel(row: ResultRow) : M

    fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M)

    /**
     * Transform a ResultRow to a fully initialized model with all attributes
     * from the result row set. This merges the model mapping logic you specified
     * with the table-specific base attribute mapping that's specific to your table
     * implementation.
     *
     * Use this to transform your result rows into models.
     */
    fun toModel(row: ResultRow) = initializeModel(row)
        .also {
            it.id = row[id].value
            it.createdAt = row[createdAt]
            it.updatedAt = row[updatedAt]
        }

    /**
     * Insert an ordered list of models
     * On failure, model ids won't be set and an exception may be thrown,
     * effectively rolling back the transaction.
     */
    fun insert(vararg models: M) = self().batchInsert(models.toList()) { model ->
        appendStatementValues(this, model)
        appendBaseStatementValues(this, model)
    }.forEachIndexed { index, resultRow ->
        models[index].also { model -> model.id = resultRow[id].value }
    }.let { models }

    /**
     * Update an ordered list of models.
     *
     * Returns true when all models are updated, otherwise false.
     */
    fun update(vararg models: M) = models.let {
        self().batchUpdate(
            models.toList(),
            id = { model ->
                model.id?.let { createEntityID(it, self()) }
                    ?: throw PersistenceError.UnpersistedUpdateError(it)
            },
            body = { model ->
                appendStatementValues(this, model)
                appendBaseStatementValues(this, model)
            }
        ) == models.size
    }

    fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M, vararg skip: Column<*>) {
        if (self().id !in skip) {
            model.id?.let { safeId -> stmt[id] = createEntityID(safeId, self()) }
        }
    }

    /** Delete a list of models */
    fun delete(vararg models: M) = models.mapNotNull { it.id }
        .let { ids ->
            val idColumn = id
            transaction {
                self().deleteWhere { idColumn inList ids }
            } == models.size
        }
}
