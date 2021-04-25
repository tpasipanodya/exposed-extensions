package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.persistence.models.Model
import io.taff.hephaestus.persistence.postgres.moment
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider.createEntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.BatchUpdateStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.time.OffsetDateTime.now

/**
 *
 */
abstract class ModelAwareTable<M : Model>(open val name: String) : LongIdTable(name) {

    val createdAt = moment("created_at").clientDefault { now() }
    val updatedAt = moment("updated_at").clientDefault { now() }

    /**
     *
     */
    private fun entityId(id: Long) = createEntityID(id, this)

    /**
     *
     */
    fun toModel(row: ResultRow) = fill(row, initializeModel(row))

    /**
     *
     */
    abstract fun initializeModel(row: ResultRow) : M

    /**
     *
     */
    protected open fun fill(row: ResultRow, model: M) = model.also {
        it.id = row[id].value
        it.createdAt = row[createdAt]
        it.updatedAt = row[updatedAt]
    }

    /**
     *
     */
    protected open fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        model.id?.let { stmt[id] = entityId(it) }
    }

    /**
     * Insert a model into the database and run the after insert callback.
     */
    fun insert(vararg models: M) = insert(models.toList()).first()

    /**
     * Insert an orderd list of models into the database and run the after insert callback.
     */
    fun insert(models: List<M>) : Iterable<M> = batchInsert(models) { fillStatement(this, it) }
        .forEachIndexed { index, resultRow ->
            models[index].also { model -> model.id = resultRow[id].value }
        }.let { models }

    /**
     * Update an ordred list of models and run the after update callbacks.
     */
    fun update(transaction: Transaction, models: Iterable<M>): Iterable<M> = models.also {
        BatchUpdateStatement(this).apply {
            models.forEach { model ->
                model.id?.let { id ->
                    addBatch(entityId(id))
                    fillStatement(this, model)
                } ?: throw SQLException("Cannot update the following model because it doesn't have an Id. Model: $model")
            }
        }.execute(transaction)
    }

    /**
     *
     */
    fun update(transaction: Transaction, model: M) = update(transaction, listOf(model)).first()

    /**
     *
     */
   open  fun delete(models: Iterable<M>) = models.also {
        val idColumn = id
        transaction {
            deleteWhere {
                idColumn inList models.mapNotNull { it.id }
            }
        }
    }

    /**
     *
     */
    open fun delete(vararg models: M) = delete(models.toList())
}

