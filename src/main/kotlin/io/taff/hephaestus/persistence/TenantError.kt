package io.taff.hephaestus.persistence

/**
 * Errors involving tenant isolation.
 */
class TenantError(msg: String) : Exception(msg)

val noTenantSetError = TenantError( "No tenant id set")