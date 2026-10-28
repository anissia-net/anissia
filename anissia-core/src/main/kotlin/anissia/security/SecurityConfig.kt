package anissia.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import anissia.support.ApiResponse
import anissia.support.Json
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    addAllowedOrigin("*")
                    addAllowedMethod("*")
                    addAllowedHeader("*")
                    allowCredentials = false
                },
            )
        }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        datAuthenticationFilter: DatAuthenticationFilter,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .requestCache { it.disable() }
            .anonymous { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers {
                it.cacheControl { cache -> cache.disable() }
                it.frameOptions { frame -> frame.disable() }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = 401
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write(Json.write(ApiResponse.fail("로그인이 필요합니다.")))
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/wiki/images").authenticated()
                it.anyRequest().permitAll()
            }
            .addFilterBefore(datAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    companion object {
        private const val BCRYPT_STRENGTH = 10
    }
}
