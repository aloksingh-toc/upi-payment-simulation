package com.upi.payment.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @InjectMocks private ApiKeyAuthFilter filter;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "configuredApiKey", "correct-key");
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerToken_setsAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer correct-key");
        // getRequestURI() is only called in shouldNotFilter(), not doFilterInternal() — no stub needed

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidBearerToken_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong-key");
        // getRemoteAddr() appears in a log.warn call; we don't assert on it — no stub needed
        // getRequestURI() is only called in shouldNotFilter(), not doFilterInternal() — no stub needed

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingAuthHeader_noAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        // getRequestURI() is only called in shouldNotFilter(), not doFilterInternal() — no stub needed

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void webhookPath_filterSkipped() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/webhooks/upi");

        assertThat(filter.shouldNotFilter(request)).isTrue();
        verifyNoInteractions(chain);
    }
}
