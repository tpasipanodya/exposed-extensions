package io.taff.hephaestus.persistence

import kotlinx.coroutines.asContextElement

/** The current tenant's id */
internal var CurrentTenantId = ThreadLocal<Any>()

/**
 * Set the current Tenant Id.
 */
fun <TI : Any> setCurrentTenantId(id: TI) = CurrentTenantId
        .get()
        .also {
            CurrentTenantId.set(id)
            CurrentTenantId.asContextElement()
        }

/**
 * Unset the current Tenant Id.
 */
fun unsetCurrentTenantId() = CurrentTenantId
        .get()
        .also { CurrentTenantId.set(it) }