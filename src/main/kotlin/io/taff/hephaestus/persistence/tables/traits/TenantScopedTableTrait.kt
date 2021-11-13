package io.taff.hephaestus.persistence.tables.traits

import io.taff.hephaestus.helpers.isNull
import io.taff.hephaestus.persistence.CurrentTenantId
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.models.TenantScopedModel
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.util.*

interface TenantScopedTableTrait<M : TenantScopedModel, T : UUIDTable>
    : ModelMappingTableTrait<M, T> {

    /** The Tenant id column. */
    val tenantId: Column<UUID>

    /** Set tenant Id on models loaded frm the DB */
    override fun toModel(row: ResultRow) = super.toModel(row)
        .also { it.tenantId = row[tenantId] }


    /** Set tenant Id on insert/update statements */
    override fun appendBaseStatementValues(stmt: UpdateBuilder<Int>, model: M) {
        CurrentTenantId.get()?.let { id ->
            if (model.tenantId.isNull() || model.tenantId == id) {
                stmt[tenantId] = id
                model.tenantId = id
            } else throw TenantError("Model ${model.id} can't be persisted because it doesn't belong to the current tenant.")
        } ?: throw TenantError("Model ${model.id} can't be persisted because There's no current tenant Id set.")
        super.appendBaseStatementValues(stmt, model)
    }

    /** Hard delete the provided records and raise a TenantError if they don't belong to the current tenant */
    override fun delete(vararg models: M) = models
        .also { validateDestruction(it) }
        .let { super.delete(*models) }

    /** Where clause for tenant isolation */
    fun currentTenantScope() = Op.build {
        CurrentTenantId.get()
            ?.let { tenantId eq it }
            ?: tenantId.isNull()
    }

    /** verify that a delete/destroy won't violate tenant isolation */
    fun validateDestruction(models: Array<out M>) = models.also {
        if (CurrentTenantId.get().isNull()) throw TenantError("Cannot destroy models because there is no CurrentTenantId.")

        models.any { model -> model.tenantId != CurrentTenantId.get() }
            .also { belongsToOtherTenant ->
                if (belongsToOtherTenant) throw TenantError("Cannot destroy models because they belong to a different tenant.")
            }
    }
}