package anissia.config

import anissia.support.AnissiaJackson
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration(proxyBeanMethods = false)
class JacksonConfig {

    @Bean
    fun anissiaJsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer =
        object : JsonMapperBuilderCustomizer, Ordered {
            override fun customize(jsonMapperBuilder: tools.jackson.databind.json.JsonMapper.Builder) {
                AnissiaJackson.customize(jsonMapperBuilder)
            }

            override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE
        }
}
