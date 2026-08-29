package anissia.security

import anissia.support.clientIp
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

class DatAuthentication(
    private val actor: Actor,
) : AbstractAuthenticationToken(actor.roles.map { SimpleGrantedAuthority("ROLE_$it") }) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = ""

    override fun getPrincipal(): Actor = actor

    override fun getName(): String = actor.name
}

@Component
class DatAuthenticationFilter(
    private val datTokenService: DatTokenService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(DatTokenService.HEADER)
        if (!header.isNullOrBlank()) {
            val actor = datTokenService.parse(header, request.clientIp)
            if (actor.isLogin) {
                SecurityContextHolder.getContext().authentication = DatAuthentication(actor)
            }
        }
        filterChain.doFilter(request, response)
    }
}
