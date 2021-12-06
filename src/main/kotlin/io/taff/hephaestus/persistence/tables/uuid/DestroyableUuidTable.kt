package io.taff.hephaestus.persistence.tables.uuid

import io.taff.hephaestus.persistence.models.DestroyableModel
import io.taff.hephaestus.persistence.tables.traits.DestroyableTableTrait
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IdTableWithDefaultScopeStriped
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant
import java.time.Instant.now
import java.util.*

/**
 * A table that supports soft-deletes by setting the `destroyed_at` column on destruction.
 * @param M The concrete model type.
 */
abstract class DestroyableUuidTable<M : DestroyableModel<UUID>>(name: String)
    : UUIDTable(name), DestroyableTableTrait<UUID, M, DestroyableUuidTable<M>> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val destroyedAt = timestamp("destroyed_at").nullable()
    override val defaultScope = { Op.build { destroyedAt.isNull() } }

    override fun self() = this

    override fun destroyed() = View(this) { Op.build { destroyedAt.isNotNull() } }

    override fun liveAndDestroyed() = View(this) {
        Op.build { destroyedAt.isNull() or destroyedAt.isNotNull() }
    }

    class View<M : DestroyableModel<UUID>>(private val actual: DestroyableUuidTable<M>,
                                           override val defaultScope: () -> Op<Boolean>)
        : DestroyableUuidTable<M>(actual.tableName), DestroyableTableTrait<UUID, M, DestroyableUuidTable<M>> {

        override val columns: List<Column<*>> = actual.columns

        override fun initializeModel(row: ResultRow) = actual.initializeModel(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, model: M) =
            actual.appendStatementValues(stmt, model)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this
    }
}


