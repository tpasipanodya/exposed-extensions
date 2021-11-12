package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.noTenantSetError
import io.taff.hephaestus.persistence.setCurrentTenantId
import java.util.*

interface TenantScopedModel : Model {

    var tenantId: UUID?

    /**
     * Perform an action as the tenant who owns this model.
     */
    fun <T> asTenant(fxn: () -> T) = tenantId?.let { safeTenantId ->
        val previousTenantId = setCurrentTenantId(safeTenantId)
        fxn().also { previousTenantId?.let { setCurrentTenantId(it) } }
    } ?: throw noTenantSetError

    /**
     * Perform an action as the tenant who owns this model.
     */
    fun asTenant(fxn: () -> Unit) {
        tenantId?.let { safeTenantId ->
            val previousTenantId = setCurrentTenantId(safeTenantId)
            fxn()
            previousTenantId?.let { setCurrentTenantId(it) }
        } ?: throw noTenantSetError
    }
}