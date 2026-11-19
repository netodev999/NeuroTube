package com.neurotube.app.innertube

import com.google.common.truth.Truth.assertThat
import io.ktor.http.HttpStatusCode
import org.junit.Test
import java.time.Instant

class RetryPolicyTest {
    @Test
    fun `a rate limit or a server error is worth asking about again`() {
        assertThat(isRetriableStatus(HttpStatusCode.TooManyRequests)).isTrue()
        assertThat(isRetriableStatus(HttpStatusCode.InternalServerError)).isTrue()
        assertThat(isRetriableStatus(HttpStatusCode.ServiceUnavailable)).isTrue()
        assertThat(isRetriableStatus(HttpStatusCode.GatewayTimeout)).isTrue()
    }

    @Test
    fun `a request that was refused on its own merits is not repeated`() {
        assertThat(isRetriableStatus(HttpStatusCode.BadRequest)).isFalse()
        assertThat(isRetriableStatus(HttpStatusCode.Unauthorized)).isFalse()
        assertThat(isRetriableStatus(HttpStatusCode.NotFound)).isFalse()
        assertThat(isRetriableStatus(HttpStatusCode.OK)).isFalse()
    }

    @Test
    fun `a Retry-After in seconds is honoured`() {
        assertThat(retryAfterMillis("7")).isEqualTo(7_000L)
        assertThat(retryAfterMillis("0")).isEqualTo(0L)
    }

    @Test
    fun `a Retry-After far in the future is capped so one header cannot stall a walk`() {
        assertThat(retryAfterMillis("3600")).isEqualTo(30_000L)
    }

    @Test
    fun `a Retry-After given as an HTTP date is honoured`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        assertThat(retryAfterMillis("Thu, 01 Jan 2026 00:00:05 GMT", now)).isEqualTo(5_000L)
    }

    @Test
    fun `a Retry-After that has already passed asks for no wait`() {
        val now = Instant.parse("2026-01-01T00:00:10Z")
        assertThat(retryAfterMillis("Thu, 01 Jan 2026 00:00:05 GMT", now)).isEqualTo(0L)
    }

    @Test
    fun `a missing or unreadable Retry-After falls back to the caller's own pause`() {
        assertThat(retryAfterMillis(null)).isNull()
        assertThat(retryAfterMillis("")).isNull()
        assertThat(retryAfterMillis("soon")).isNull()
    }
}
