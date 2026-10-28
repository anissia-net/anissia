package anissia.wiki.media

import java.time.Duration

data class MediaSettings(
    val ffmpegPath: String = "ffmpeg",
    val ffprobePath: String = "ffprobe",
    val timeout: Duration = Duration.ofSeconds(120),
    val maxSourceBytes: Long = 30L * 1024 * 1024,
    val maxOutputBytes: Long = 64L * 1024 * 1024,
    val maxPixels: Long = 40_000_000,
    val maxAnimationPixels: Long = 500_000_000,
    val maxFrames: Int = 500,
    val maxDuration: Duration = Duration.ofSeconds(120),
    val allowAnimated: Boolean = true,
    val maxAnimationFps: Int = 20,
    val stillCrf: Int = 24,
    val animationCrf: Int = 28,
    val cpuUsed: Int = 6,
    val threads: Int = 2,
) {
    init {
        require(ffmpegPath.isNotBlank() && ffprobePath.isNotBlank())
        require(!timeout.isNegative && !timeout.isZero && timeout <= Duration.ofMinutes(10))
        require(maxSourceBytes in 1..Int.MAX_VALUE.toLong() && maxOutputBytes in 1..Int.MAX_VALUE.toLong())
        require(maxPixels in 1..100_000_000 && maxAnimationPixels in maxPixels..2_000_000_000)
        require(maxFrames in 1..10_000 && maxAnimationFps in 1..60)
        require(!maxDuration.isNegative && !maxDuration.isZero && maxDuration <= Duration.ofMinutes(10))
        require(stillCrf in 0..63 && animationCrf in 0..63 && cpuUsed in 0..8 && threads in 1..8)
    }
}

data class ImageInfo(
    val width: Int,
    val height: Int,
    val frames: Int,
    val durationMillis: Long,
    val animated: Boolean,
    val hasAlpha: Boolean,
)

data class ConvertedImage(
    val width: Int,
    val height: Int,
    val frames: Int,
    val durationMillis: Long,
    val animated: Boolean,
    val hasAlpha: Boolean,
    val bytes: Long,
    val mimeType: String = "image/avif",
)

enum class MediaFailure { INVALID_IMAGE, LIMIT_EXCEEDED, TIMEOUT, UNAVAILABLE, CONVERSION_FAILED }

class MediaException(val reason: MediaFailure, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

internal fun invalidImage(message: String): Nothing = throw MediaException(MediaFailure.INVALID_IMAGE, message)
internal fun mediaLimit(message: String): Nothing = throw MediaException(MediaFailure.LIMIT_EXCEEDED, message)
