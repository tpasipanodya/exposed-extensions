package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.records.SoftDeletableRecord
import io.taff.exposed.extensions.tables.traits.SoftDeletableTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant.now
import java.util.*

/**
 * A table that supports soft-deletes by setting the `soft_deleted_at` column on destruction.
 * @param M The concrete record type.
 */
abstract class SoftDeletableUuidTable<M : SoftDeletableRecord<UUID>>(name: String)
    : UUIDTable(name), SoftDeletableTableTrait<UUID, M, SoftDeletableUuidTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val softDeletedAt = timestamp("soft_deleted_at").nullable()
    override val defaultFilter = { Op.build { softDeletedAt.isNull() } }

    override fun self() = this

    override fun softDeleted() = View(this) { Op.build { softDeletedAt.isNotNull() } }

    override fun liveAndSoftDeleted() = View(this) {
        Op.build { softDeletedAt.isNull() or softDeletedAt.isNotNull() }
    }

    class View<M : SoftDeletableRecord<UUID>>(private val actual: SoftDeletableUuidTable<M>,
                                              override val defaultFilter: () -> Op<Boolean>)
        : SoftDeletableUuidTable<M>(actual.tableName), SoftDeletableTableTrait<UUID, M, SoftDeletableUuidTable<M>> {

        override val columns: List<Column<*>> = actual.columns

        override fun initializeRecord(row: ResultRow) = actual.initializeRecord(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: M) =
            actual.appendStatementValues(stmt, record)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this
    }
}


