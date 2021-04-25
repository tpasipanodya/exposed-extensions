package io.taff.hephaestus.persistence.models

import java.time.OffsetDateTime

interface DestroyableTenantScopedModel<TI> : TenantScopedModel<TI> {

    var destroyedAt: OffsetDateTime?

    fun markAsDestroyed() {
        destroyedAt = OffsetDateTime.now()
    }
}