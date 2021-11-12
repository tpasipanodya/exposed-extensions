package io.taff.hephaestus.persistence

import kotlinx.coroutines.asContextElement
import java.util.*

/** The current tenant's id */
internal var CurrentTenantId = ThreadLocal<UUID>()

/**
 * Set the current Tenant Id.
 */
fun setCurrentTenantId(id: UUID) = CurrentTenantId
        .get()
        .also {
            CurrentTenantId.set(id)
            CurrentTenantId.asContextElement()
        }

/**
 * Unset the current Tenant Id.
 */
fun clearCurrentTenantId() = CurrentTenantId
        .get()
        .also { CurrentTenantId.set(null) }