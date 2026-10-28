package anissia.wiki.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode

class ImageConverterTest {
    @TempDir
    lateinit var directory: Path
    private lateinit var settings: MediaSettings

    @BeforeEach
    fun binaries() {
        settings = MediaSettings(
            ffmpegPath = System.getenv("ANISSIA_MEDIA_TEST_FFMPEG") ?: "ffmpeg",
            ffprobePath = System.getenv("ANISSIA_MEDIA_TEST_FFPROBE") ?: "ffprobe",
            timeout = Duration.ofSeconds(30),
        )
    }

    private fun requireConverter() {
        val available = runCatching {
            val encoders = MediaProcess().capture(listOf(settings.ffmpegPath, "-hide_banner", "-encoders"),
                MediaDeadline(Duration.ofSeconds(5))).decodeToString()
            "libaom-av1" in encoders
        }.getOrDefault(false)
        assumeTrue(available, "FFmpeg with libaom-av1 is required; use ANISSIA_MEDIA_TEST_FFMPEG and ANISSIA_MEDIA_TEST_FFPROBE")
    }

    private fun png(name: String, alpha: Boolean): Path {
        val image = BufferedImage(24, 18, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            image.setRGB(x, y, if (alpha && x < 8) 0x50ff0000 else 0xff4080c0.toInt())
        }
        return directory.resolve(name).also { ImageIO.write(image, "png", it.toFile()) }
    }

    @Test
    fun `static image preserves dimensions and real transparency`() {
        requireConverter()
        val converter = ImageConverter(settings)
        val transparent = converter.convert(png("transparent.png", true), directory.resolve("transparent.avif"))
        assertEquals(24, transparent.width)
        assertEquals(18, transparent.height)
        assertEquals(1, transparent.frames)
        assertTrue(transparent.hasAlpha)
        assertFalse(transparent.animated)
        val opaque = converter.convert(png("opaque.png", false), directory.resolve("opaque.avif"))
        assertFalse(opaque.hasAlpha)
        assertTrue(opaque.bytes > 0)
    }

    @Test
    fun `large landscape and portrait images fit 512 without stretching or upscaling`() {
        requireConverter()
        val converter = ImageConverter(settings)
        for ((width, height) in listOf(1200 to 600, 600 to 1200, 25 to 37, 1 to 1200)) {
            val input = directory.resolve("$width-$height.png")
            ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", input.toFile())
            val converted = converter.convert(input, directory.resolve("$width-$height.avif"))
            val max = maxOf(width, height)
            assertEquals(if (max > 512) (width * 512 / max).coerceAtLeast(1) else width, converted.width)
            assertEquals(if (max > 512) (height * 512 / max).coerceAtLeast(1) else height, converted.height)
        }
    }

    @Test
    fun `thumbnail crops the long side at center and produces an opaque 64 square`() {
        requireConverter()
        val converter = ImageConverter(settings)
        for ((width, height) in listOf(300 to 100, 100 to 300)) {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until height) for (x in 0 until width) {
                val center = if (width > height) x in 100 until 200 else y in 100 until 200
                image.setRGB(x, y, if (center) 0xff00ff00.toInt() else 0x00ff0000)
            }
            val input = directory.resolve("crop-$width.png")
            ImageIO.write(image, "png", input.toFile())
            val result = converter.convertThumbnail(input, directory.resolve("crop-$width.avif"))
            assertEquals(64, result.width)
            assertEquals(64, result.height)
            assertEquals(1, result.frames)
            assertFalse(result.hasAlpha)
        }
    }

    @Test
    fun `animated thumbnail contains only first frame and preserves transparency`() {
        requireConverter()
        val result = ImageConverter(settings).convertThumbnail(gif(2), directory.resolve("thumbnail.avif"))
        assertEquals(64, result.width)
        assertEquals(64, result.height)
        assertEquals(1, result.frames)
        assertEquals(0L, result.durationMillis)
        assertFalse(result.animated)
        assertTrue(result.hasAlpha)
    }

    @Test
    fun `animated GIF retains duration loop count and transparency`() {
        requireConverter()
        val input = gif(2)
        val result = ImageConverter(settings).convert(input, directory.resolve("animation.avif"))
        assertEquals(3, result.frames)
        assertEquals(300L, result.durationMillis)
        assertTrue(result.animated)
        assertTrue(result.hasAlpha)
        assertEquals(3, ImageContainer.loops(Files.readAllBytes(directory.resolve("animation.avif")), ImageKind.AVIF))
    }

    @Test
    fun `large animated image is resized while retaining playback and transparency`() {
        requireConverter()
        val output = directory.resolve("large-animation.avif")
        val result = ImageConverter(settings).convert(gif(2, 800, 400), output)
        assertEquals(512, result.width)
        assertEquals(256, result.height)
        assertEquals(3, result.frames)
        assertEquals(300L, result.durationMillis)
        assertTrue(result.animated)
        assertTrue(result.hasAlpha)
        assertEquals(3, ImageContainer.loops(Files.readAllBytes(output), ImageKind.AVIF))
    }

    @Test
    fun `animated frame and duration limits are checked before conversion`() {
        requireConverter()
        val input = gif(0)
        assertEquals(MediaFailure.LIMIT_EXCEEDED, assertThrows(MediaException::class.java) {
            ImageConverter(settings.copy(maxFrames = 2)).probe(input)
        }.reason)
        assertEquals(MediaFailure.LIMIT_EXCEEDED, assertThrows(MediaException::class.java) {
            ImageConverter(settings.copy(maxDuration = Duration.ofMillis(200))).probe(input)
        }.reason)
    }

    @Test
    fun `invalid file and byte limit are rejected without executing tools`() {
        val input = directory.resolve("fake.png")
        Files.writeString(input, "this is not an image")
        val absent = settings.copy(ffmpegPath = "/does/not/exist", ffprobePath = "/does/not/exist")
        assertEquals(MediaFailure.INVALID_IMAGE, assertThrows(MediaException::class.java) { ImageConverter(absent).probe(input) }.reason)
        assertEquals(MediaFailure.LIMIT_EXCEEDED, assertThrows(MediaException::class.java) {
            ImageConverter(absent.copy(maxSourceBytes = 2)).probe(input)
        }.reason)
    }

    private fun gif(repeats: Int, width: Int = 24, height: Int = 18): Path {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val file = directory.resolve("input-$repeats.gif")
        ImageIO.createImageOutputStream(file.toFile()).use { stream ->
            writer.output = stream
            writer.prepareWriteSequence(null)
            repeat(3) { frame ->
                val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                for (y in 0 until height) for (x in 8 until width) image.setRGB(x, y, if (frame % 2 == 0) 0xffff0000.toInt() else 0xff00ff00.toInt())
                val type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image)
                val metadata = writer.getDefaultImageMetadata(type, writer.defaultWriteParam)
                val root = metadata.getAsTree("javax_imageio_gif_image_1.0") as IIOMetadataNode
                val control = root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
                control.setAttribute("disposalMethod", "restoreToBackgroundColor")
                control.setAttribute("userInputFlag", "FALSE")
                control.setAttribute("transparentColorFlag", "TRUE")
                control.setAttribute("delayTime", "10")
                control.setAttribute("transparentColorIndex", "0")
                if (frame == 0) {
                    val applications = IIOMetadataNode("ApplicationExtensions")
                    val application = IIOMetadataNode("ApplicationExtension")
                    application.setAttribute("applicationID", "NETSCAPE")
                    application.setAttribute("authenticationCode", "2.0")
                    application.userObject = byteArrayOf(1, repeats.toByte(), (repeats shr 8).toByte())
                    applications.appendChild(application)
                    root.appendChild(applications)
                }
                metadata.setFromTree("javax_imageio_gif_image_1.0", root)
                writer.writeToSequence(IIOImage(image, null, metadata), writer.defaultWriteParam)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return file
    }
}
