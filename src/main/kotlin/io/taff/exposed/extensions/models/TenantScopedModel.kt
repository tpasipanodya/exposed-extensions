package io.taff.exposed.extensions.models

import io.taff.exposed.extensions.noTenantSetError
import io.taff.exposed.extensions.setCurrentTenantId


/**
 * Models that belong to a tenant
 *
 * @param ID the id type.
 * @param TID the id type.
 */
interface TenantScopedModel<ID : Comparable<ID>, TID : Comparable<TID>> : Model<ID> {

    var tenantId: TID?

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
