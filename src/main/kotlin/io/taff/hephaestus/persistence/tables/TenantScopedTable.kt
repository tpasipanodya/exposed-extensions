package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.persistence.CurrentTenantId
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder

/**
 *
 */
abstract class TenantScopedTable<TI, M : TenantScopedModel<TI>>(override var name: String) : ModelAwareTable<M>(name) {

    /**  */
    abstract val tenantId: Column<TI>

    /** */
    fun tenantScope() = Op.build { tenantId eq CurrentTenantId.get() as TI }

    /** */
    override fun fill(row: ResultRow, model: M) = model.also {
        it.tenantId = row[tenantId]
        super.fill(row, model)
    }

    /** */
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        (CurrentTenantId.get() as TI?)
            ?.let { id ->
                stmt[tenantId] = id
                model.tenantId = id
        }
        super.fillStatement(stmt, model)
    }

    /** */
    fun scopedSelect(query: SqlExpressionBuilder.() -> Op<Boolean>) = select { query() and tenantScope() }

    /**
     *
     */
    fun scopedSelect() = select { tenantScope() }

    /** */
    fun scopedDelete(where: SqlExpressionBuilder.() -> Op<Boolean>) = deleteWhere { tenantScope() and where(this) }

}
