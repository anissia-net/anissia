package anissia.wiki.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class MediaProcessTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `process output is bounded before it can exhaust memory`() {
        val error = assertThrows(MediaException::class.java) {
            MediaProcess().capture(listOf("/bin/sh", "-c", "while :; do printf 0123456789; done"),
                MediaDeadline(Duration.ofSeconds(5)), 1024)
        }
        assertEquals(MediaFailure.LIMIT_EXCEEDED, error.reason)
    }

    @Test
    fun `deadline kills the running converter`() {
        val pidFile = directory.resolve("pid")
        val error = assertThrows(MediaException::class.java) {
            MediaProcess().capture(listOf("/bin/sh", "-c", "echo \$\$ > \"\$1\"; exec sleep 10", "test", pidFile.toString()),
                MediaDeadline(Duration.ofMillis(400)))
        }
        assertEquals(MediaFailure.TIMEOUT, error.reason)
        val pid = Files.readString(pidFile).trim().toLong()
        ProcessHandle.of(pid).ifPresent { process ->
            process.onExit().get(2, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(process.isAlive)
        }
    }

    @Test
    fun `all subprocesses consume one shared deadline`() {
        val runner = MediaProcess()
        val deadline = MediaDeadline(Duration.ofMillis(600))
        runner.capture(listOf("/bin/sh", "-c", "sleep 0.4"), deadline)
        val error = assertThrows(MediaException::class.java) {
            runner.capture(listOf("/bin/sh", "-c", "sleep 0.4"), deadline)
        }
        assertEquals(MediaFailure.TIMEOUT, error.reason)
    }
}
