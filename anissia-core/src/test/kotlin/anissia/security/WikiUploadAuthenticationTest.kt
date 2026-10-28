package anissia.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockServletContext
import org.springframework.security.web.FilterChainProxy
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext

class WikiUploadAuthenticationTest {
    private lateinit var context: AnnotationConfigWebApplicationContext
    private lateinit var filters: FilterChainProxy

    @Configuration(proxyBeanMethods = false)
    class Fixtures {
        @Bean fun tokens(): DatTokenService = mock(DatTokenService::class.java).also {
            `when`(it.parse("invalid", "127.0.0.1")).thenReturn(Actor())
            `when`(it.parse("valid", "127.0.0.1")).thenReturn(Actor(an = 7))
        }
        @Bean fun datFilter(tokens: DatTokenService) = DatAuthenticationFilter(tokens)
    }

    @BeforeEach
    fun start() {
        context = AnnotationConfigWebApplicationContext().apply {
            servletContext = MockServletContext()
            register(SecurityConfig::class.java, Fixtures::class.java)
            refresh()
        }
        filters = context.getBean("springSecurityFilterChain", FilterChainProxy::class.java)
    }

    @AfterEach fun stop() { context.close() }

    private fun request(method: String, token: String? = null): Pair<MockHttpServletResponse, Boolean> {
        val request = MockHttpServletRequest(method, "/wiki/images").apply {
            servletPath = "/wiki/images"
            contentType = "multipart/form-data; boundary=broken"
            setContent("invalid multipart body".toByteArray())
            if (token != null) addHeader("dat", token)
        }
        val response = MockHttpServletResponse()
        var reachedMultipartHandler = false
        filters.doFilter(request, response, FilterChain { _, _ -> reachedMultipartHandler = true })
        return response to reachedMultipartHandler
    }

    @Test
    fun `anonymous and invalid sessions are rejected before multipart processing`() {
        for (token in listOf(null, "invalid")) {
            val (response, reached) = request("POST", token)
            assertEquals(401, response.status)
            assertFalse(reached)
            assertTrue(response.contentAsString.contains("로그인이 필요합니다."))
        }
    }

    @Test
    fun `authenticated upload reaches account age policy and public listing remains readable`() {
        assertTrue(request("POST", "valid").second)
        assertTrue(request("GET").second)
    }
}
