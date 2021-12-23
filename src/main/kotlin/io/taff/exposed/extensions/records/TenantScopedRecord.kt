package io.taff.exposed.extensions.records

import io.taff.exposed.extensions.noTenantSetError
import io.taff.exposed.extensions.setCurrentTenantId


/**
 * Records that belong to a tenant
 *
 * @param ID the id type.
 * @param TID the id type.
 */
interface TenantScopedRecord<ID : Comparable<ID>, TID : Comparable<TID>> :
    Record<ID> {

    var tenantId: TID?

    /**
     * Perform an action as the tenant who owns this record.
     */
    fun <T> asTenant(fxn: () -> T) = tenantId?.let { safeTenantId ->
        val previousTenantId = setCurrentTenantId(safeTenantId)
        fxn().also { previousTenantId?.let { setCurrentTenantId(it) } }
    } ?: throw noTenantSetError

    /**
     * Perform an action as the tenant who owns this record.
     */
    fun asTenant(fxn: () -> Unit) {
        tenantId?.let { safeTenantId ->
            val previousTenantId = setCurrentTenantId(safeTenantId)
            fxn()
            previousTenantId?.let { setCurrentTenantId(it) }
        } ?: throw noTenantSetError
    }
}
