package anissia.security

class RecordCodec(
    private val version: String,
    private val recordCount: Int,
    private val separator: Char = 0x1e.toChar(),
) {
    private val separatorText = separator.toString()

    init {
        require(version.isNotEmpty()) { "version must not be empty" }
        require(!version.contains(separator)) { "version must not contain separator" }
        require(recordCount > 0) { "minimum record count is 1, but was $recordCount" }
    }

    fun read(text: String): List<String> {
        val split = text.split(separator)
        return if (recordCount == (split.size - 1) && split[0] == version) {
            split.subList(1, split.size)
        } else {
            listOf()
        }
    }

    fun write(vararg records: String): String =
        version + separator + records.joinToString(separatorText)
}
