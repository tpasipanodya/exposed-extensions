package io.taff.exposed.extensions.tables.long

import io.taff.exposed.extensions.records.SoftDeletableRecord
import io.taff.exposed.extensions.records.TenantScopedRecord
import io.taff.exposed.extensions.tables.traits.TenantScopedSoftDeletableTableTrait
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.time.Instant.now

/**
 * A table that enforces tenant isolation unless explicitly requested not to.
 * Also supports soft deletes by setting the`soft_deleted_at` column on destruction.
 *
 * @param TID The concrete tenantId type.
 * @param M The concrete record type.
 */
abstract class TenantScopedSoftDeletableLongIdTable<TID : Comparable<TID>, M>(name: String, )
    : LongIdTable(name),
    TenantScopedSoftDeletableTableTrait<Long, TID, M, TenantScopedSoftDeletableLongIdTable<TID, M>>
        where M : TenantScopedRecord<Long, TID>, M : SoftDeletableRecord<Long> {

    override val createdAt = timestamp("created_at").clientDefault { now() }
    override val updatedAt = timestamp("updated_at").clientDefault { now() }
    override val softDeletedAt = timestamp("soft_deleted_at").nullable()
    override val defaultScope = { Op.build { currentTenantScope() and softDeletedAt.isNull() } }

    override fun self() = this

    override fun softDeleted() = View(this) {
        Op.build { currentTenantScope() and softDeletedAt.isNotNull() }
    }

    override fun liveAndSoftDeleted() = View(this) {
        Op.build { currentTenantScope() }
    }

    override fun softDeletedForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { softDeletedAt.isNotNull() }
    }

    override fun liveAndSoftDeletedForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { softDeletedAt.isNull() or softDeletedAt.isNotNull() }
    }

    override fun liveForAllTenants() = VIewWithTenantScopeStriped(this) {
        Op.build { softDeletedAt.isNull() }
    }

    open class View<TID : Comparable<TID>, M>(private val actual: TenantScopedSoftDeletableLongIdTable<TID, M>,
        override val defaultScope: () -> Op<Boolean>)
        : TenantScopedSoftDeletableLongIdTable<TID, M>(actual.tableName),
        TenantScopedSoftDeletableTableTrait<Long, TID, M, TenantScopedSoftDeletableLongIdTable<TID, M>>
            where M : TenantScopedRecord<Long, TID>, M :  SoftDeletableRecord<Long> {

        override val columns: List<Column<*>> = actual.columns

        override val tenantId = actual.tenantId

        override fun initializeRecord(row: ResultRow) = actual.initializeRecord(row)

        override fun appendStatementValues(stmt: UpdateBuilder<Int>, record: M) =
            actual.appendStatementValues(stmt, record)

        override fun describe(s: Transaction, queryBuilder: QueryBuilder) = actual.describe(s, queryBuilder)

        override fun self() = this

    }

    class VIewWithTenantScopeStriped<TID : Comparable<TID>, M>(actual: TenantScopedSoftDeletableLongIdTable<TID, M>,
        override val defaultScope: () -> Op<Boolean>) : View<TID, M>(actual, defaultScope)
            where M : TenantScopedRecord<Long, TID>, M :  SoftDeletableRecord<Long> {
        override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, record: M, vararg skip: Column<*>) {
            super.appendBaseStatementValues(stmt, record, tenantId, *skip)
        }
        override fun validateDestruction(records: Array<out M>) = records
    }
}
