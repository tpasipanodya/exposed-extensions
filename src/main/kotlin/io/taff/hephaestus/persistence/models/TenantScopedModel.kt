package io.taff.hephaestus.persistence.models

interface TenantScopedModel<TI> : Model {

    var tenantId: TI?
}
