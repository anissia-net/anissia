package anissia.push

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "anissia.push")
class PushProperties(
    val vapidPublicKey: String = "",
    val vapidPrivateKey: String = "",
    val vapidSubject: String = "",
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PushProperties::class)
class PushConfig
