package anissia.wiki.media

import java.nio.ByteBuffer
import java.nio.ByteOrder

internal enum class ImageKind(val codecs: Set<String>) {
    PNG(setOf("png", "apng")), JPEG(setOf("mjpeg")), GIF(setOf("gif")),
    WEBP(setOf("webp", "webp_anim")), AVIF(setOf("av1")), BMP(setOf("bmp")),
}

internal object ImageContainer {
    fun kind(data: ByteArray): ImageKind = when {
        data.starts(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)) -> ImageKind.PNG
        data.starts(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) -> ImageKind.JPEG
        data.starts("GIF87a") || data.starts("GIF89a") -> ImageKind.GIF
        data.starts("RIFF") && data.matches(8, "WEBP") -> ImageKind.WEBP
        data.starts("BM") -> ImageKind.BMP
        box(data, "ftyp")?.let { payload ->
            payload.size >= 8 && (payload.matches(0, "avif") || payload.matches(0, "avis") ||
                (8 until payload.size step 4).any { payload.matches(it, "avif") || payload.matches(it, "avis") })
        } == true -> ImageKind.AVIF
        else -> invalidImage("지원하지 않는 이미지 형식입니다.")
    }

    fun loops(data: ByteArray, kind: ImageKind): Int = when (kind) {
        ImageKind.GIF -> gifLoops(data)
        ImageKind.WEBP -> {
            var offset = 12
            var result = 1L
            while (offset.toLong() + 8 <= data.size) {
                val length = uint32(data, offset + 4, ByteOrder.LITTLE_ENDIAN)
                val end = checkedEnd(data, offset, length + 8 + length % 2)
                if (data.matches(offset, "ANIM")) {
                    if (length < 6) invalidContainer()
                    result = uint16(data, offset + 12).toLong()
                    break
                }
                offset = end
            }
            loopValue(result)
        }
        ImageKind.PNG -> {
            var offset = 8
            var result = 1L
            while (offset.toLong() + 8 <= data.size) {
                val length = uint32(data, offset)
                val end = checkedEnd(data, offset, length + 12)
                if (data.matches(offset + 4, "acTL")) {
                    if (length != 8L) invalidContainer()
                    result = uint32(data, offset + 12)
                    break
                }
                offset = end
            }
            loopValue(result)
        }
        ImageKind.AVIF -> avifLoops(data)
        else -> 1
    }

    private fun gifLoops(data: ByteArray): Int {
        checkedEnd(data, 0, 13)
        var offset = 13 + if (data[10].toInt() and 0x80 != 0) 3 * (2 shl (data[10].toInt() and 7)) else 0
        while (true) {
            checkedEnd(data, offset, 1)
            when (data[offset++].toInt() and 255) {
                0x3b -> return 1
                0x21 -> {
                    checkedEnd(data, offset, 2)
                    val label = data[offset++].toInt() and 255
                    val size = data[offset].toInt() and 255
                    if (label == 255 && size == 11 &&
                        (data.matches(offset + 1, "NETSCAPE2.0") || data.matches(offset + 1, "ANIMEXTS1.0"))) {
                        checkedEnd(data, offset, 17)
                        if (data[offset + 12] != 3.toByte() || data[offset + 13] != 1.toByte() ||
                            data[offset + 16] != 0.toByte()) invalidContainer()
                        val repeats = uint16(data, offset + 14)
                        return if (repeats == 0) 0 else repeats + 1
                    }
                    offset = skipSubblocks(data, offset)
                }
                0x2c -> {
                    checkedEnd(data, offset, 9)
                    val packed = data[offset + 8].toInt() and 255
                    offset = checkedEnd(data, offset, 10L + if (packed and 128 != 0) 3 * (2 shl (packed and 7)) else 0)
                    offset = skipSubblocks(data, offset)
                }
                else -> invalidContainer()
            }
        }
    }

    private fun skipSubblocks(data: ByteArray, initial: Int): Int {
        var offset = initial
        while (true) {
            checkedEnd(data, offset, 1)
            val length = data[offset++].toInt() and 255
            if (length == 0) return offset
            offset = checkedEnd(data, offset, length.toLong())
        }
    }

    private fun avifLoops(data: ByteArray): Int {
        val moov = box(data, "moov") ?: return 1
        val trak = box(moov, "trak") ?: invalidContainer()
        val edts = box(trak, "edts") ?: return 1
        val elst = box(edts, "elst") ?: invalidContainer()
        checkedEnd(elst, 0, 4)
        if (elst[3].toInt() and 1 == 0) return 1
        val tkhd = box(trak, "tkhd") ?: invalidContainer()
        checkedEnd(tkhd, 0, 1)
        val total = when (tkhd[0].toInt()) {
            0 -> uint32(tkhd, 20)
            1 -> uint64(tkhd, 28)
            else -> invalidContainer()
        }
        if (total == -1L || total == Long.MAX_VALUE || total == 0xffffffffL) return 0
        val cycle = when (elst[0].toInt()) {
            0 -> uint32(elst, 8)
            1 -> uint64(elst, 8)
            else -> invalidContainer()
        }
        if (total <= 0 || cycle <= 0) invalidContainer()
        return loopValue(total / cycle + if (total % cycle != 0L) 1 else 0)
    }

    private fun box(data: ByteArray, name: String): ByteArray? {
        var offset = 0
        while (offset.toLong() + 8 <= data.size) {
            val size = uint32(data, offset)
            val header = if (size == 1L) 16 else 8
            val length = when (size) {
                0L -> (data.size - offset).toLong()
                1L -> uint64(data, offset + 8)
                else -> size
            }
            if (length < header) invalidContainer()
            val end = checkedEnd(data, offset, length)
            if (data.matches(offset + 4, name)) return data.copyOfRange(offset + header, end)
            offset = end
        }
        return null
    }

    private fun checkedEnd(data: ByteArray, offset: Int, length: Long): Int {
        if (offset < 0 || length < 0 || length > data.size.toLong() - offset) invalidContainer()
        return offset + length.toInt()
    }

    private fun uint16(data: ByteArray, offset: Int): Int {
        checkedEnd(data, offset, 2)
        return (data[offset].toInt() and 255) or ((data[offset + 1].toInt() and 255) shl 8)
    }

    private fun uint32(data: ByteArray, offset: Int, order: ByteOrder = ByteOrder.BIG_ENDIAN): Long {
        checkedEnd(data, offset, 4)
        return ByteBuffer.wrap(data, offset, 4).order(order).int.toLong() and 0xffffffffL
    }

    private fun uint64(data: ByteArray, offset: Int): Long {
        checkedEnd(data, offset, 8)
        return ByteBuffer.wrap(data, offset, 8).long
    }

    private fun loopValue(value: Long): Int {
        if (value !in 0..Int.MAX_VALUE.toLong()) invalidContainer()
        return value.toInt()
    }

    private fun ByteArray.starts(value: String): Boolean = matches(0, value)
    private fun ByteArray.starts(value: ByteArray): Boolean = size >= value.size && value.indices.all { this[it] == value[it] }
    private fun ByteArray.matches(offset: Int, value: String): Boolean =
        offset >= 0 && offset.toLong() + value.length <= size && value.indices.all { this[offset + it].toInt() == value[it].code }

    private fun invalidContainer(): Nothing = invalidImage("이미지 컨테이너가 손상되었습니다.")
}
