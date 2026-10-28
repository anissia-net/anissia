package anissia.wiki.upload

import anissia.wiki.media.ImageConverter
import anissia.wiki.media.MediaSettings
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("anissia.wiki.upload")
data class WikiUploadProperties(
    var enabled: Boolean = false,
    var tempRoot: String = "./data/wiki/temp",
    var publicRoot: String = "./data/wiki/public",
    var originalRoot: String = "./data/wiki/originals",
    var publicBaseUrl: String = "http://localhost:8001/wiki/files",
    var retainOriginals: Boolean = false,
    var serveFiles: Boolean = false,
    var maxBytes: Long = 10L * 1024 * 1024,
    var maxQueueSize: Int = 20,
    var maxPendingPerAccount: Int = 3,
    var ffmpegPath: String = "ffmpeg",
    var ffprobePath: String = "ffprobe",
    var timeoutSeconds: Long = 120,
)

@Configuration
@EnableConfigurationProperties(WikiUploadProperties::class)
class WikiUploadConfiguration {
    @Bean
    fun wikiImageConverter(properties: WikiUploadProperties) = ImageConverter(
        MediaSettings(
            ffmpegPath = properties.ffmpegPath,
            ffprobePath = properties.ffprobePath,
            timeout = Duration.ofSeconds(properties.timeoutSeconds),
            maxSourceBytes = properties.maxBytes,
        ),
    )
}
