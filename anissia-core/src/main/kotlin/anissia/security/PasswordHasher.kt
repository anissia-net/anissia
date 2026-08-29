package anissia.security

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordHasher(
    private val passwordEncoder: PasswordEncoder,
) {
    fun hash(rawPassword: String): String = requireNotNull(passwordEncoder.encode(rawPassword))

    fun matches(rawPassword: String, encodedPassword: String): Boolean =
        passwordEncoder.matches(rawPassword, encodedPassword)
}
