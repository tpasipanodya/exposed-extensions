package io.taff.exposed.extensions.tables.uuid

import io.taff.exposed.extensions.models.SoftDeletableModel
import io.taff.exposed.extensions.tables.traits.SoftDeletableTableTrait
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant.now
import java.util.*

/**
 * A table that supports soft-deletes by setting the `soft_deleted_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class SoftDeletableUuidTable<M : SoftDeletableModel<UUID>>(name: String)
    : UUIDTable(name), SoftDeletableTableTrait<UUID, M, SoftDeletableUuidTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val softDeletedAt = timestamp("soft_deleted_at").nullable()
    override val defaultScope = { Op.build { softDeletedAt.isNull() } }

    override fun self() = this

    override fun softDeleted() = View(this) { Op.build { softDeletedAt.isNotNull() } }

    override fun liveAndSoftDeleted() = View(this) {
        Op.build { softDeletedAt.isNull() or softDeletedAt.isNotNull() }
    }

    class View<M : SoftDeletableModel<UUID>>(private val actual: SoftDeletableUuidTable<M>,
                                           override val defaultScope: () -> Op<Boolean>)
        : SoftDeletableUuidTable<M>(actual.tableName), SoftDeletableTableTrait<UUID, M, SoftDeletableUuidTable<M>> {

        override val columns: List<Column<*>> = actual.columns

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this
    }
}


