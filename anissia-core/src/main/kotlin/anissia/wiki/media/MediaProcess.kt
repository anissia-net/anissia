package anissia.wiki.media

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class MediaDeadline(timeout: Duration) {
    private val end = System.nanoTime() + timeout.toNanos()

    fun remaining(): Long = (end - System.nanoTime()).also {
        if (it <= 0) throw MediaException(MediaFailure.TIMEOUT, "이미지 변환 시간이 초과되었습니다.")
    }
}

internal class MediaProcess {
    fun capture(arguments: List<String>, deadline: MediaDeadline, maxBytes: Long = 2L * 1024 * 1024): ByteArray {
        val output = ByteArrayOutputStream()
        consume(arguments, deadline, maxBytes) { bytes, count ->
            output.write(bytes, 0, count)
            true
        }
        return output.toByteArray()
    }

    fun consume(
        arguments: List<String>,
        deadline: MediaDeadline,
        maxBytes: Long,
        accept: (ByteArray, Int) -> Boolean,
    ): Long {
        deadline.remaining()
        val process = try {
            ProcessBuilder(arguments).redirectInput(ProcessBuilder.Redirect.PIPE).start()
        } catch (error: IOException) {
            throw MediaException(MediaFailure.UNAVAILABLE, "이미지 변환 도구를 실행할 수 없습니다.", error)
        }
        process.outputStream.close()
        val stoppedEarly = AtomicBoolean(false)
        val readFailure = AtomicReference<Throwable?>()
        val stderr = ByteArrayOutputStream()
        val output = CompletableFuture<Long>()
        val errors = CompletableFuture<Unit>()
        val outputThread = Thread.ofVirtual().name("wiki-media-output").start {
            try {
                var total = 0L
                val buffer = ByteArray(64 * 1024)
                process.inputStream.use { stream ->
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) mediaLimit("변환 도구의 출력 제한을 초과했습니다.")
                        if (!accept(buffer, count)) {
                            stoppedEarly.set(true)
                            terminate(process)
                            break
                        }
                    }
                }
                output.complete(total)
            } catch (error: Throwable) {
                readFailure.compareAndSet(null, error)
                terminate(process)
                output.completeExceptionally(error)
            }
        }
        val errorThread = Thread.ofVirtual().name("wiki-media-errors").start {
            try {
                val buffer = ByteArray(4096)
                process.errorStream.use { stream ->
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (stderr.size() + count > 64 * 1024) {
                            mediaLimit("변환 도구의 오류 출력 제한을 초과했습니다.")
                        }
                        stderr.write(buffer, 0, count)
                    }
                }
                errors.complete(Unit)
            } catch (error: Throwable) {
                if (!stoppedEarly.get()) readFailure.compareAndSet(null, error)
                terminate(process)
                errors.completeExceptionally(error)
            }
        }
        try {
            if (!process.waitFor(deadline.remaining(), TimeUnit.NANOSECONDS)) {
                throw MediaException(MediaFailure.TIMEOUT, "이미지 변환 시간이 초과되었습니다.")
            }
            val count = output.get(deadline.remaining(), TimeUnit.NANOSECONDS)
            if (!stoppedEarly.get()) errors.get(deadline.remaining(), TimeUnit.NANOSECONDS)
            readFailure.get()?.let { throw it }
            if (!stoppedEarly.get() && (process.exitValue() != 0 || stderr.size() != 0)) {
                throw MediaException(MediaFailure.CONVERSION_FAILED, "이미지를 분석하거나 변환할 수 없습니다.")
            }
            return count
        } catch (error: TimeoutException) {
            throw MediaException(MediaFailure.TIMEOUT, "이미지 변환 시간이 초과되었습니다.", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw MediaException(MediaFailure.CONVERSION_FAILED, "이미지 변환이 중단되었습니다.", error)
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (cause is MediaException) throw cause
            throw MediaException(MediaFailure.CONVERSION_FAILED, "이미지 변환 결과를 읽을 수 없습니다.", cause)
        } finally {
            terminate(process)
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            outputThread.interrupt()
            errorThread.interrupt()
        }
    }

    private fun terminate(process: Process) {
        process.descendants().use { descendants -> descendants.forEach { it.destroyForcibly() } }
        if (process.isAlive) process.destroyForcibly()
    }
}
