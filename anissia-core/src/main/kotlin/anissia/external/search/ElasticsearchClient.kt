package anissia.external.search

import jakarta.annotation.PreDestroy
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.util.EntityUtils
import org.elasticsearch.client.Request
import org.elasticsearch.client.Response
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
@ConditionalOnProperty(name = ["anissia.search.mock"], havingValue = "false", matchIfMissing = true)
class ElasticsearchClient(
    @Value("\${elasticsearch.url}") url: String,
    @Value("\${elasticsearch.username}") username: String,
    @Value("\${elasticsearch.password}") password: String,
) : SearchClient {

    private val restClient: RestClient = RestClient
        .builder(HttpHost.create(url))
        .setHttpClientConfigCallback { httpClientBuilder ->
            httpClientBuilder.setDefaultCredentialsProvider(
                BasicCredentialsProvider().apply {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        setCredentials(AuthScope.ANY, UsernamePasswordCredentials(username, password))
                    }
                },
            )
        }
        .build()

    override fun request(method: String, endpoint: String, body: String?): SearchResponse {
        val request = Request(method, endpoint)
        if (body != null) {
            request.setJsonEntity(body)
        }
        return try {
            restClient.performRequest(request).toSearchResponse()
        } catch (e: ResponseException) {
            e.response.toSearchResponse()
        }
    }

    private fun Response.toSearchResponse(): SearchResponse =
        SearchResponse(
            status = statusLine.statusCode,
            body = entity?.let { EntityUtils.toString(it, StandardCharsets.UTF_8) } ?: "",
        )

    @PreDestroy
    fun close() {
        restClient.close()
    }
}
