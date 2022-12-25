package io.taff.exposed.extensions.tables.traits

import io.taff.exposed.extensions.PersistenceError
import io.taff.exposed.extensions.records.Record
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider.createEntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Record mapping behavior applicable to tables.
 *
 * @param ID The record's concrete id type.
 * @param M The record's concrete type.
 */
interface RecordMappingTableTrait<ID : Comparable<ID>, M : Record<ID>, T : IdTable<ID>> {

    val id: Column<EntityID<ID>>
    val createdAt: Column<Instant>
    val updatedAt: Column<Instant>
    fun self() : T

    fun initializeRecord(row: ResultRow) : M

    fun appendStatementValues(stmt: UpdateBuilder<Int>, record: M)

    /**
     * Transform a ResultRow to a fully initialized record with all attributes
     * from the result row set. This merges the record mapping logic you specified
     * with the table-specific base attribute mapping that's specific to your table
     * implementation.
     *
     * Use this to transform your result rows into records.
     */
    fun toRecord(row: ResultRow) = initializeRecord(row)
        .also {
            it.id = row[id].value
            it.createdAt = row[createdAt]
            it.updatedAt = row[updatedAt]
        }

    /**
     * Insert an ordered list of records
     * On failure, record ids won't be set and an exception may be thrown,
     * effectively rolling back the transaction.
     */
    fun insert(vararg records: M) = self().batchInsert(records.toList()) { record ->
        appendStatementValues(this, record)
        appendBaseStatementValues(this, record)
    }.forEachIndexed { index, resultRow ->
        records[index].also { record -> record.id = resultRow[id].value }
    }.let { records }

    /**
     * Update an ordered list of records.
     *
     * Returns true when all records are updated, otherwise false.
     */
    fun update(vararg records: M) = records.let {
        self().batchUpdate(
            records.toList(),
            id = { record ->
                record.id?.let { createEntityID(it, self()) }
                    ?: throw PersistenceError.UnpersistedUpdateError(it)
            },
            body = { record ->
                appendStatementValues(this, record)
                appendBaseStatementValues(this, record)
            }
        ) == records.size
    }

    fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
        if (self().id !in skip) {
            record.id?.let { safeId -> stmt[id] = createEntityID(safeId, self()) }
        }
    }

    /** Delete a list of record */
    fun delete(vararg records: M) = records.mapNotNull { it.id }
        .let { ids ->
            val idColumn = id
            transaction {
                self().deleteWhere { Op.build { idColumn inList ids } }
            } == records.size
        }
}
