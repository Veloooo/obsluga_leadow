package pl.warmescape.obsluga_leadow.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import mu.two.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.OncePerRequestFilter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


@Configuration
class RequestLoggingFilterConfig() : OncePerRequestFilter() {
    private val maxPayloadLength = 10_000
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val logger = KotlinLogging.logger {}

        val wrappedRequest = CachedBodyHttpServletRequest(request)
        val requestBody = wrappedRequest.cachedBody.take(maxPayloadLength)
        // Log the request details
        logger.info(
            "REQUEST DATA: ${wrappedRequest.method} ${wrappedRequest.requestURI}, " +
                    "QueryString: ${wrappedRequest.queryString ?: "N/A"}, " +
                    "Body: $requestBody",
        )

        filterChain.doFilter(wrappedRequest, response)
    }
}

class CachedBodyHttpServletRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {
    val cachedBody: String

    init {
        val stringBuilder = StringBuilder()
        BufferedReader(InputStreamReader(request.inputStream, StandardCharsets.UTF_8)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = reader.readLine()
            }
        }
        cachedBody = stringBuilder.toString()
    }

    override fun getInputStream(): ServletInputStream {
        val byteArrayInputStream = ByteArrayInputStream(cachedBody.toByteArray(StandardCharsets.UTF_8))
        return object : ServletInputStream() {
            override fun isFinished(): Boolean = byteArrayInputStream.available() == 0

            override fun isReady(): Boolean = true

            override fun read(): Int = byteArrayInputStream.read()

            override fun setReadListener(readListener: ReadListener?) {}
        }
    }

    override fun getReader(): BufferedReader {
        return BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
    }
}