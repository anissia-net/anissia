package anissia.wiki.media

internal data class FrameTiming(val startMicros: Long, val durationMicros: Long)

internal data class FramePlan(val indices: List<Int>, val timings: List<FrameTiming>) {
    fun filter(source: List<FrameTiming>): String {
        if (timings == source && indices.size == source.size) return ""
        val select = balancedSum(indices.map { "eq(n,$it)" })
        val pts = balancedSum(timings.mapIndexed { index, timing -> "eq(N,$index)*${timing.startMicros}" })
        return "select='$select',settb=1/1000000,setpts='$pts',"
    }

    fun durationsFilter(): String =
        "setts=duration='if(eq(N,${timings.lastIndex}),${timings.last().durationMicros}/1000000/TB,NEXT_PTS-PTS)'"

    companion object {
        fun create(source: List<FrameTiming>, maxFps: Int): FramePlan {
            val original = FramePlan(source.indices.toList(), source)
            val step = (1_000_000L + maxFps - 1) / maxFps
            if (source.size <= 1 || source.all { it.durationMicros >= step }) return original
            val duration = source.last().startMicros + source.last().durationMicros
            if (duration < step * 2) invalidImage("프레임 제한을 적용하기에 움직이는 이미지가 너무 짧습니다.")
            val indices = mutableListOf<Int>()
            val starts = mutableListOf<Long>()
            var index = 0
            for (tick in 0 until duration / step) {
                val start = tick * step
                while (index + 1 < source.size && source[index + 1].startMicros <= start) index++
                if (indices.lastOrNull() != index) {
                    indices += index
                    starts += start
                }
            }
            if (indices.size == 1) {
                indices += source.lastIndex
                starts += duration - step
            }
            return FramePlan(indices, starts.mapIndexed { position, start ->
                FrameTiming(start, (starts.getOrNull(position + 1) ?: duration) - start)
            })
        }

        private fun balancedSum(terms: List<String>): String = when (terms.size) {
            0 -> "0"
            1 -> terms.first()
            else -> {
                val middle = terms.size / 2
                "(${balancedSum(terms.subList(0, middle))}+${balancedSum(terms.subList(middle, terms.size))})"
            }
        }
    }
}
