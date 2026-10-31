package com.lumaos.pro.launcher.launcher

/** Caller serializes access with the cache and render configuration. */
internal class IconRequestTracker<K> {
    private val pending = mutableMapOf<K, Any>()

    fun start(key: K): Any? {
        if (pending.containsKey(key)) return null
        return Any().also { pending[key] = it }
    }

    fun isCurrent(key: K, token: Any): Boolean = pending[key] === token

    fun finish(key: K, token: Any): Boolean {
        if (!isCurrent(key, token)) return false
        pending.remove(key)
        return true
    }

    fun invalidate(key: K) { pending.remove(key) }
    fun invalidateWhere(predicate: (K) -> Boolean) { pending.keys.removeAll(predicate) }
    fun clear() { pending.clear() }
}
