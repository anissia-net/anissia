package anissia.security

import me.saro.dat.dat.DatCmsManager
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "dat")
data class DatProperties(
    val uri: String = "http://localhost:8088",
    val token: String = "",
    val intervalSeconds: Long = 60L,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatProperties::class)
class DatConfig {

    @Bean
    fun datCmsManager(properties: DatProperties): DatCmsManager =
        DatCmsManager.builder()
            .uri(properties.uri)
            .token(properties.token)
            .intervalSeconds(properties.intervalSeconds)
            .build()
}
