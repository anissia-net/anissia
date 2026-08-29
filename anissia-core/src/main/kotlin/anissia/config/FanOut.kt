package anissia.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

@Component
class FanOut(
    @Value("\${anissia.concurrency.fan-out-limit:16}") private val limit: Int,
) {
    fun <T, R> map(items: Collection<T>, task: (T) -> R): List<R> {
        if (items.isEmpty()) {
            return emptyList()
        }
        if (items.size == 1) {
            return listOf(task(items.first()))
        }
        val gate = Semaphore(limit)
        return Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            items
                .map { item ->
                    executor.submit<R> {
                        gate.acquire()
                        try {
                            task(item)
                        } finally {
                            gate.release()
                        }
                    }
                }
                .map { future ->
                    try {
                        future.get()
                    } catch (e: ExecutionException) {
                        throw e.cause ?: e
                    }
                }
        }
    }

    fun <T> forEach(items: Collection<T>, task: (T) -> Unit) {
        map(items, task)
    }
}
