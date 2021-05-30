package io.taff.hephaestus.persistence.models

import io.taff.hephaestus.persistence.CurrentTenantId
import io.taff.hephaestus.persistence.TenantError
import io.taff.hephaestus.persistence.setCurrentTenantId

interface TenantScopedModel<TI> : Model {

    var tenantId: TI?

    fun <T> asCurrent(fxn: () -> T) = tenantId?.let {
        setCurrentTenantId(it)
            .also { prevTenantId ->
                fxn()
                setCurrentTenantId(prevTenantId)
            }
    } ?: throw TenantError("Cannot set an unpersisted tenant as current")

    fun <T> asCurrent(fxn: () -> Nothing) {
        tenantId?.let {
            setCurrentTenantId(it)
                .also { prevTenantId ->
                    fxn()
                    setCurrentTenantId(prevTenantId)
                }
        } ?: throw TenantError("Cannot set an unpersisted tenant as current")
    }
}