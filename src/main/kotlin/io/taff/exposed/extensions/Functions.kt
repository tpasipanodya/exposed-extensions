package io.taff.exposed.extensions

import kotlinx.coroutines.asContextElement

/** The current tenant's id */
internal var CurrentTenantId = ThreadLocal<Any>()

/**
 * Set the current Tenant Id.
 */
fun <ID> setCurrentTenantId(id: ID) = (CurrentTenantId.get() as ID)
        .let {
            CurrentTenantId.set(id)
            CurrentTenantId.asContextElement()
        }

/**
 * Unset the current Tenant Id.
 */
fun <ID> clearCurrentTenantId() = (CurrentTenantId.get() as ID)
        .also { CurrentTenantId.set(null) }
