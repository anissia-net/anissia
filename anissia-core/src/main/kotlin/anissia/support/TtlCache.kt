package anissia.support

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlin.time.Duration
import kotlin.time.toJavaDuration

fun <K : Any, V : Any> ttlCache(ttl: Duration, maximumSize: Long = 10_000): Cache<K, V> =
    Caffeine.newBuilder()
        .expireAfterWrite(ttl.toJavaDuration())
        .maximumSize(maximumSize)
        .build()

fun <K : Any, V : Any> Cache<K, V>.getOrLoad(key: K, loader: (K) -> V): V =
    requireNotNull(get(key) { loader(it) })
