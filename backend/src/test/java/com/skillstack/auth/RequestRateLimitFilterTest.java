package com.skillstack.auth;

import com.skillstack.common.ApiErrorWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RequestRateLimitFilterTest {
    @Test
    void boundsExpensiveRequestsWithoutSharingBucketsAcrossCallers() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var filter = new RequestRateLimitFilter(
                new RateLimitPolicy(true, 2, 2, 2, 2, 2),
                new ApiErrorWriter(new ObjectMapper()), metrics,
                Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC));

        assertThat(run(filter, "192.0.2.10").getStatus()).isEqualTo(200);
        assertThat(run(filter, "192.0.2.10").getStatus()).isEqualTo(200);
        var rejected = run(filter, "192.0.2.10");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getContentAsString()).contains("RATE_LIMITED");
        assertThat(run(filter, "192.0.2.11").getStatus()).isEqualTo(200);
        assertThat(metrics.counter("skillstack.rate_limit.rejected", "category", "analysis").count()).isEqualTo(1);
    }

    private MockHttpServletResponse run(RequestRateLimitFilter filter, String address) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/operations/recommendations");
        request.setRemoteAddr(address);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
