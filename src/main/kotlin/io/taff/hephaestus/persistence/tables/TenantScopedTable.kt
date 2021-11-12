package io.taff.hephaestus.persistence.tables

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.CurrentTenantId
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.util.*

/**
 * A table that provides tenant isolation via query methods prefixed with `scoped`.
 */
abstract class TenantScopedTable<M : TenantScopedModel>(override var name: String) : ModelMappingTable<M>(name) {

    /** The Tenant id column. */
    abstract val tenantId: Column<UUID>

    /** Where clause for tenant isolation */
    private fun tenantScope() = Op.build {
        (CurrentTenantId.get() as UUID?)
            ?.let { tenantId eq it }
            ?: tenantId.isNull()
    }

    /** Set tenant Id on models loaded frm the DB */
    override fun fillModel(row: ResultRow, model: M) = model.also {
        it.tenantId = row[tenantId]
        super.fillModel(row, model)
    }

    /** Set tenant Id on insert/update statements */
    override fun fillStatement(stmt: UpdateBuilder<Int>, model: M) {
        CurrentTenantId.get()?.let { id ->
              if (model.tenantId.isNull() || model.tenantId == id) {
                  stmt[tenantId] = id
                  model.tenantId = id
              } else throw TenantError("Model ${model.id} can't be persisted because it doesn't belong to the current tenant.")
        } ?: throw TenantError("Model ${model.id} can't be persisted because There's no current tenant Id set.")
        super.fillStatement(stmt, model)
    }

    /** Hard delete the provided records and raise a TenantError if they don't belong to the current tenant */
    override fun delete(vararg models: M) = models
        .also { validateDestruction(it) }
        .let { super.delete(*models) }

    /** verify that a delete/destroy won't violate tenant isolation */
    protected fun validateDestruction(models: Array<out M>) = models.also {
        if (CurrentTenantId.get().isNull()) throw TenantError("Cannot destroy models because there is no CurrentTenantId.")

        models.any { model -> model.tenantId != CurrentTenantId.get() }
            .also { belongsToOtherTenant ->
                if (belongsToOtherTenant) throw TenantError("Cannot destroy models because they belong to a different tenant.")
            }
    }
}
