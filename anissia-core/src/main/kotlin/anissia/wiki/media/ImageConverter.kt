package anissia.wiki.media

import anissia.support.Json
import tools.jackson.databind.JsonNode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.roundToLong

open class ImageConverter(private val settings: MediaSettings = MediaSettings()) {
    private val processes = MediaProcess()

    open fun probe(input: Path): ImageInfo = inspect(input, MediaDeadline(settings.timeout), settings.maxSourceBytes).info

    open fun convert(input: Path, output: Path): ConvertedImage = convert(input, output, false)

    open fun convertThumbnail(input: Path, output: Path): ConvertedImage = convert(input, output, true)

    private fun convert(input: Path, output: Path, thumbnail: Boolean): ConvertedImage {
        val deadline = MediaDeadline(settings.timeout)
        val source = inspect(input, deadline, settings.maxSourceBytes)
        val destination = output.toAbsolutePath().normalize()
        if (Files.exists(destination, NOFOLLOW_LINKS)) {
            throw MediaException(MediaFailure.CONVERSION_FAILED, "변환 결과 경로가 이미 존재합니다.")
        }
        val directory = destination.parent
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) {
            throw MediaException(MediaFailure.CONVERSION_FAILED, "변환 결과 디렉터리가 없습니다.")
        }
        val candidate = Files.createTempFile(directory, ".wiki-media-", ".avif")
        try {
            val plan = if (thumbnail) FramePlan(listOf(0), listOf(FrameTiming(0, 0)))
                else FramePlan.create(source.timings, settings.maxAnimationFps)
            val (width, height) = dimensions(source.info, thumbnail)
            processes.capture(encodingArguments(input, candidate, source, plan, width, height, thumbnail), deadline)
            val result = inspect(candidate, deadline, settings.maxOutputBytes)
            if (result.kind != ImageKind.AVIF || result.info.width != width || result.info.height != height ||
                result.info.frames != plan.indices.size || (!source.info.hasAlpha && result.info.hasAlpha) ||
                (!thumbnail && source.info.animated && (result.loops != source.loops || !matchingTimings(plan.timings, result.timings)))) {
                throw MediaException(MediaFailure.CONVERSION_FAILED, "이미지 변환 결과가 원본의 재생 정보와 일치하지 않습니다.")
            }
            deadline.remaining()
            val bytes = Files.size(candidate)
            Files.move(candidate, destination)
            return ConvertedImage(width, height, result.info.frames, result.info.durationMillis,
                result.info.animated, result.info.hasAlpha, bytes)
        } catch (error: IOException) {
            throw MediaException(MediaFailure.CONVERSION_FAILED, "이미지 변환 파일을 저장할 수 없습니다.", error)
        } finally {
            Files.deleteIfExists(candidate)
        }
    }

    private fun inspect(input: Path, deadline: MediaDeadline, maxBytes: Long): InspectedImage {
        deadline.remaining()
        if (!Files.isRegularFile(input, NOFOLLOW_LINKS)) invalidImage("이미지 파일이 없습니다.")
        val size = Files.size(input)
        if (size <= 0 || size > maxBytes) mediaLimit("이미지 파일 용량 제한을 초과했습니다.")
        val bytes = Files.newInputStream(input, NOFOLLOW_LINKS).use { it.readNBytes((maxBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        if (bytes.size.toLong() > maxBytes) mediaLimit("이미지 파일 용량 제한을 초과했습니다.")
        val kind = ImageContainer.kind(bytes)
        val metadata = probeJson(input, false, deadline)
        val streams = metadata.array("streams")
        if (streams.isEmpty() || streams.size > 4 || streams.any { it.text("codec_type") != "video" }) {
            invalidImage("이미지 스트림이 올바르지 않습니다.")
        }
        val stream = streams.filter { !it.text("pix_fmt").startsWith("gray") }
            .maxByOrNull { it.text("nb_frames").toLongOrNull() ?: 1 }
            ?: streams.maxBy { it.text("nb_frames").toLongOrNull() ?: 1 }
        val codec = stream.text("codec_name")
        if (codec !in kind.codecs) invalidImage("이미지 파일 형식과 내용이 일치하지 않습니다.")
        var width = dimension(stream, "width")
        var height = dimension(stream, "height")
        val pixels = width.toLong() * height
        if (pixels > settings.maxPixels) mediaLimit("이미지 해상도 제한을 초과했습니다.")
        val primaryIndex = stream.path("index").asInt(-1)
        if (primaryIndex < 0) invalidImage("이미지 스트림 번호가 올바르지 않습니다.")
        val frames = probeJson(input, true, deadline).array("frames")
        val primary = frames.filter { it.path("stream_index").asInt(-1) == primaryIndex }
        if (primary.isEmpty() || primary.size > settings.maxFrames) mediaLimit("이미지 프레임 수 제한을 초과했습니다.")
        if (pixels * primary.size > settings.maxAnimationPixels) mediaLimit("움직이는 이미지의 총 해상도 제한을 초과했습니다.")
        if (primary.any { dimension(it, "width") != width || dimension(it, "height") != height }) {
            invalidImage("이미지 프레임 크기가 일치하지 않습니다.")
        }
        val rotation = rotation(stream) ?: rotation(primary.first()) ?: 0
        if (Math.floorMod(rotation, 180) == 90) width = height.also { height = width }
        val alphaStream = if (kind == ImageKind.AVIF) {
            streams.firstOrNull {
                it.path("index").asInt(-1) != primaryIndex && it.text("pix_fmt").startsWith("gray") &&
                    it.path("nb_frames") == stream.path("nb_frames") && it.path("duration") == stream.path("duration")
            }?.path("index")?.asInt(-1)
        } else null
        val timings = primary.map { FrameTiming(micros(it, "best_effort_timestamp_time"), micros(it, "duration_time")) }
        val animated = primary.size > 1
        val duration = timings.last().let { it.startMicros + it.durationMicros }
        if (animated && !settings.allowAnimated) invalidImage("움직이는 이미지 업로드는 지원하지 않습니다.")
        if (animated && (duration <= 0 || duration > settings.maxDuration.toNanos() / 1000)) {
            mediaLimit("움직이는 이미지의 재생 시간 제한을 초과했습니다.")
        }
        if (timings.first().startMicros != 0L || timings.any { it.startMicros < 0 || it.durationMicros < 0 } ||
            (animated && (timings.any { it.durationMicros == 0L } || timings.zipWithNext().any { (a, b) -> a.startMicros >= b.startMicros }))) {
            invalidImage("이미지의 프레임 시간이 올바르지 않습니다.")
        }
        if (alphaStream != null) {
            val alphaFrames = frames.filter { it.path("stream_index").asInt(-1) == alphaStream }
            if (alphaFrames.size != primary.size || alphaFrames.zip(primary).any { (alpha, color) ->
                    alpha.path("width") != color.path("width") || alpha.path("height") != color.path("height") ||
                        micros(alpha, "best_effort_timestamp_time") != micros(color, "best_effort_timestamp_time")
                }) invalidImage("이미지 투명도 프레임이 일치하지 않습니다.")
        }
        val alphaPossible = alphaStream != null || hasAlpha(stream.text("pix_fmt")) || primary.any { hasAlpha(it.text("pix_fmt")) }
        val preliminary = InspectedImage(
            ImageInfo(width, height, primary.size, if (animated) (duration + 999) / 1000 else 0, animated, alphaPossible),
            kind, codec, primaryIndex, alphaStream, ImageContainer.loops(bytes, kind), timings,
            colorTag(stream, "color_primaries", "bt709"), colorTag(stream, "color_transfer", "iec61966-2-1"),
        )
        if (!alphaPossible) return preliminary
        return preliminary.copy(info = preliminary.info.copy(hasAlpha = hasTransparency(input, preliminary, deadline)))
    }

    private fun probeJson(input: Path, frames: Boolean, deadline: MediaDeadline): JsonNode {
        val arguments = command(true).apply {
            if (frames) addAll(listOf("-read_intervals", "%+#${(settings.maxFrames + 1) * 2 + 4}",
                "-show_frames", "-show_entries", "frame=stream_index,width,height,pix_fmt,best_effort_timestamp_time,duration_time:frame_side_data"))
            else addAll(listOf("-show_streams", "-show_format"))
            addAll(listOf("-of", "json", input.toAbsolutePath().toString()))
        }
        val bytes = processes.capture(arguments, deadline)
        return try { Json.mapper.readTree(bytes) } catch (error: Exception) {
            throw MediaException(MediaFailure.INVALID_IMAGE, "이미지 분석 결과가 올바르지 않습니다.", error)
        }
    }

    private fun hasTransparency(input: Path, image: InspectedImage, deadline: MediaDeadline): Boolean {
        val arguments = command(false).apply {
            if (image.codec == "av1") addAll(listOf("-c:v", "libdav1d"))
            addAll(listOf("-nostdin", "-i", input.toAbsolutePath().toString(),
                "-map", "0:${image.alphaStream ?: image.colorStream}", "-filter_threads", "1", "-vf",
                if (image.alphaStream != null) "format=gray16le" else "alphaextract,format=gray16le",
                "-fps_mode", "passthrough", "-f", "rawvideo", "pipe:1"))
        }
        val expected = image.info.width.toLong() * image.info.height * image.info.frames * 2
        var transparent = false
        val total = processes.consume(arguments, deadline, expected) { buffer, count ->
            transparent = (0 until count).any { buffer[it] != 255.toByte() }
            !transparent
        }
        if (!transparent && total != expected) invalidImage("이미지 투명도를 끝까지 검사할 수 없습니다.")
        return transparent
    }

    private fun dimensions(info: ImageInfo, thumbnail: Boolean): Pair<Int, Int> {
        if (thumbnail) return 64 to 64
        val largest = maxOf(info.width, info.height)
        var width = if (largest > 512) (info.width.toLong() * 512 / largest).toInt().coerceAtLeast(1) else info.width
        var height = if (largest > 512) (info.height.toLong() * 512 / largest).toInt().coerceAtLeast(1) else info.height
        if (info.animated && width > 1 && height > 1) {
            width = width and -2
            height = height and -2
        }
        return width to height
    }

    private fun encodingArguments(input: Path, output: Path, source: InspectedImage, plan: FramePlan,
                                  width: Int, height: Int, thumbnail: Boolean): List<String> {
        val info = source.info
        val animated = info.animated && !thumbnail
        val crop = if (thumbnail) "crop=${minOf(info.width, info.height)}:${minOf(info.width, info.height)}:exact=1," else ""
        val resize = "${crop}scale=$width:$height:flags=lanczos+accurate_rnd:out_color_matrix=bt709:out_range=full,setsar=1"
        val base = (source.alphaStream?.let { "[0:${source.colorStream}][0:$it]alphamerge," }
            ?: "[0:${source.colorStream}]") + plan.filter(source.timings)
        val format = if (!animated) "yuv444p10le" else if (width > 1 && height > 1) "yuv420p10le" else "yuv444p"
        val color = "$resize,format=$format,setparams=color_primaries=${source.primaries}:color_trc=${source.transfer}:colorspace=bt709:range=full"
        val filters = if (info.hasAlpha) {
            "${base}split[color][alpha];[color]$color[c];[alpha]format=rgba64le,alphaextract,${crop}scale=$width:$height:flags=lanczos+accurate_rnd,format=gray10le,setparams=colorspace=bt709:range=full[a]"
        } else "$base$color[c]"
        return command(false).apply {
            if (source.codec == "av1") addAll(listOf("-c:v", "libdav1d"))
            addAll(listOf("-nostdin", "-xerror", "-err_detect", "explode", "-i", input.toAbsolutePath().toString(),
                "-filter_complex_threads", "1", "-filter_complex", filters, "-map", "[c]"))
            if (info.hasAlpha) addAll(listOf("-map", "[a]"))
            addAll(listOf("-map_metadata", "-1", "-an", "-sn", "-dn", "-c:v", "libaom-av1",
                "-cpu-used", settings.cpuUsed.toString(), "-threads", settings.threads.toString(), "-row-mt", "1",
                "-b:v", "0", "-crf:v:0", (if (animated) settings.animationCrf else settings.stillCrf).toString(),
                "-fps_mode", "passthrough", "-enc_time_base", "1:1000000", "-color_range:v:0", "pc",
                "-colorspace:v:0", "bt709", "-color_primaries:v:0", source.primaries, "-color_trc:v:0", source.transfer))
            if (info.hasAlpha) addAll(listOf("-crf:v:1", "0", "-colorspace:v:1", "bt709", "-color_range:v:1", "pc"))
            if (!animated) addAll(listOf("-still-picture", "1"))
            else if (plan.timings != source.timings) addAll(listOf("-bsf:v", plan.durationsFilter()))
            addAll(listOf("-loop", (if (thumbnail) 1 else source.loops).toString(), "-movie_timescale", "1000000", "-f", "avif",
                "-fs", settings.maxOutputBytes.toString(), "-y", output.toString()))
        }
    }

    private fun command(probe: Boolean): MutableList<String> = mutableListOf(
        if (probe) settings.ffprobePath else settings.ffmpegPath, "-hide_banner", "-v", "error",
        "-max_alloc", "268435456", "-protocol_whitelist", "file,pipe", "-threads", settings.threads.toString(),
    )

    private fun JsonNode.array(name: String): List<JsonNode> = path(name).takeIf { it.isArray }?.toList() ?: emptyList()
    private fun JsonNode.text(name: String): String = path(name).asString("")
    private fun dimension(node: JsonNode, name: String): Int = node.path(name).asLong(0).let {
        if (it !in 1..Int.MAX_VALUE.toLong()) invalidImage("이미지 해상도가 올바르지 않습니다.")
        it.toInt()
    }
    private fun micros(node: JsonNode, name: String): Long {
        val value = node.text(name).toDoubleOrNull() ?: return 0
        if (!value.isFinite() || abs(value) > 3600) invalidImage("이미지 재생 시간이 올바르지 않습니다.")
        return (value * 1_000_000).roundToLong()
    }
    private fun rotation(node: JsonNode): Int? = node.array("side_data_list")
        .firstOrNull { it.has("rotation") }?.path("rotation")?.asInt(0)
    private fun hasAlpha(format: String): Boolean =
        listOf("yuva", "gbrap", "rgba", "bgra", "argb", "abgr", "ya", "ayuv").any(format::startsWith)
    private fun matchingTimings(first: List<FrameTiming>, second: List<FrameTiming>): Boolean =
        first.size == second.size && first.zip(second).all { (a, b) ->
            abs(a.startMicros - b.startMicros) <= 1000 && abs(a.durationMicros - b.durationMicros) <= 1000
        }
    private fun colorTag(node: JsonNode, name: String, fallback: String): String {
        val allowed = if (name == "color_primaries") {
            setOf("bt709", "bt470m", "bt470bg", "smpte170m", "smpte240m", "film", "bt2020", "smpte428", "smpte431", "smpte432", "jedec-p22", "ebu3213", "vgamut")
        } else {
            setOf("bt709", "bt470m", "bt470bg", "smpte170m", "smpte240m", "linear", "log100", "log316", "iec61966-2-4", "bt1361e", "iec61966-2-1", "bt2020-10", "bt2020-12", "smpte2084", "smpte428", "arib-std-b67", "vlog")
        }
        return node.text(name).takeIf { it in allowed } ?: fallback
    }

    private data class InspectedImage(
        val info: ImageInfo,
        val kind: ImageKind,
        val codec: String,
        val colorStream: Int,
        val alphaStream: Int?,
        val loops: Int,
        val timings: List<FrameTiming>,
        val primaries: String,
        val transfer: String,
    )
}
