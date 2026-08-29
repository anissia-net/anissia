package anissia.support

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.jacksonTypeRef
import java.io.File

object AnissiaJackson {
    fun kotlinModule(): KotlinModule =
        KotlinModule.Builder()
            .configure(KotlinFeature.NullIsSameAsDefault, true)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build()

    fun customize(builder: JsonMapper.Builder): JsonMapper.Builder =
        builder
            .addModule(kotlinModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
}

object Json {
    val mapper: JsonMapper = AnissiaJackson.customize(JsonMapper.builder()).build()

    fun write(value: Any): String = mapper.writeValueAsString(value)

    inline fun <reified T> read(json: String): T = mapper.readValue(json, jacksonTypeRef<T>())

    inline fun <reified T> read(file: File): T = mapper.readValue(file, jacksonTypeRef<T>())

    fun readTree(json: String): JsonNode = mapper.readTree(json)

    fun objectNode(): ObjectNode = mapper.createObjectNode()
}
