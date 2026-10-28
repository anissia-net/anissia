package anissia.wiki.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class ImageContainerTest {
    @Test
    fun `GIF repeats are converted to total play count`() {
        fun gif(repeats: Int): ByteArray = "GIF89a".toByteArray() +
            byteArrayOf(1, 0, 1, 0, 0, 0, 0, 0x21, 0xff.toByte(), 11) + "NETSCAPE2.0".toByteArray() +
            byteArrayOf(3, 1, repeats.toByte(), (repeats shr 8).toByte(), 0, 0x3b)
        assertEquals(0, ImageContainer.loops(gif(0), ImageKind.GIF))
        assertEquals(1, ImageContainer.loops("GIF89a".toByteArray() + byteArrayOf(1, 0, 1, 0, 0, 0, 0, 0x3b), ImageKind.GIF))
        assertEquals(3, ImageContainer.loops(gif(2), ImageKind.GIF))
    }

    @Test
    fun `container lengths cannot wrap or escape the supplied bytes`() {
        val bytes = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10) +
            ByteBuffer.allocate(4).putInt(-1).array() + "acTL".toByteArray()
        val error = assertThrows(MediaException::class.java) { ImageContainer.loops(bytes, ImageKind.PNG) }
        assertEquals(MediaFailure.INVALID_IMAGE, error.reason)
    }

    @Test
    fun `AVIF brand must be in the file type brands rather than the minor version`() {
        val valid = ByteBuffer.allocate(4).putInt(20).array() + "ftypmif1".toByteArray() + byteArrayOf(0, 0, 0, 0) + "avif".toByteArray()
        assertEquals(ImageKind.AVIF, ImageContainer.kind(valid))
        val invalid = ByteBuffer.allocate(4).putInt(16).array() + "ftypmif1avif".toByteArray()
        assertThrows(MediaException::class.java) { ImageContainer.kind(invalid) }
    }

    @Test
    fun `frame limiting keeps the final time and uses distinct source frames`() {
        val source = (0 until 30).map { FrameTiming(it * 10_000L, 10_000L) }
        val plan = FramePlan.create(source, 20)
        assertEquals(listOf(0, 5, 10, 15, 20, 25), plan.indices)
        assertEquals(300_000L, plan.timings.last().let { it.startMicros + it.durationMicros })
        assertEquals(6, plan.indices.toSet().size)
    }
}
