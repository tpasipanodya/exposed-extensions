package io.taff.exposed.extensions

import kotlinx.coroutines.asContextElement

/** The current tenant's id */
internal var CurrentTenantId = ThreadLocal<Any>()

fun <ID> currentTenantId() = CurrentTenantId.get() as ID

/**
 * Set the current Tenant Id.
 */
fun <ID> setCurrentTenantId(id: ID) =  CurrentTenantId.set(id)
        .let { CurrentTenantId.asContextElement() }

/**
 * Unset the current Tenant Id.
 */
fun <ID> clearCurrentTenantId() = (CurrentTenantId.get() as ID)
        .also { CurrentTenantId.set(null) }


/**
 * Returns true if this == null
 */
fun Any?.isNull() = this == null


/**
 * Load values from the environment and coalesce them to the specified return type.
 */
inline fun <reified V> env(key: String, default: V? = null) = (when (V::class) {
    String::class -> System.getenv(key)?.trimEnd()
    Int::class -> System.getenv(key)?.trimEnd()?.toInt()
    Long::class -> System.getenv(key)?.trimEnd()?.toLong()
    else -> null
} ?: default)
    .let {
        if (it == null) throw RuntimeException("No environment variable defined for key $key")
        it as V
    }
