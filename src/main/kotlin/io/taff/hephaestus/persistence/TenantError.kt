package io.taff.hephaestus.persistence

/**
 * Errors involving tenant isolation.
 */
class TenantError(msg: String) : Exception(msg)

/**
 * Error raised when no tenant id is set when it's expected to be set.
 */
val noTenantSetError = TenantError( "No tenant id set")
