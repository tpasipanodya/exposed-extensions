package io.taff.hephaestus.helpers

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
