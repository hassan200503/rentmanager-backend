package com.rentmanager.shared.security.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TraceIdFilterTest {

    private TraceIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    /** Captures what the MDC held while the chain was executing. */
    private FilterChain capturingChain(AtomicReference<String> seen) {
        return (req, res) -> seen.set(MDC.get(TraceIdFilter.TRACE_ID_KEY));
    }

    @Test
    void aTraceIdIsAvailableInTheMdcForTheDurationOfTheRequest() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seen));

        assertThat(seen.get())
                .as("ErrorTrackingService reads MDC traceId; nothing set it before this filter")
                .isNotBlank();
    }

    @Test
    void theTraceIdIsEchoedSoAUserCanQuoteAReferenceThatFindsTheRequest() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seen));

        assertThat(response.getHeader(TraceIdFilter.REQUEST_ID_HEADER)).isEqualTo(seen.get());
    }

    @Test
    void anInboundRequestIdIsAdoptedSoATraceContinuesAcrossServices() throws Exception {
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "edge-abc123");
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seen));

        assertThat(seen.get()).isEqualTo("edge-abc123");
    }

    /**
     * The header lands in every log line for the request. An unvalidated
     * value containing a newline lets a caller forge log entries, which is
     * how log injection works — and a forged entry in an audit investigation
     * is worse than a missing one.
     */
    @Test
    void aHeaderContainingANewlineIsRejectedRatherThanLogged() throws Exception {
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER,
                "abc\n2026-09-01 ERROR [forged] Payment approved by admin");
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seen));

        assertThat(seen.get()).doesNotContain("\n");
        assertThat(seen.get()).doesNotContain("forged");
    }

    @Test
    void otherPunctuationIsAlsoRefused() throws Exception {
        for (String hostile : new String[]{"a b", "a\tb", "a\rb", "a%0Ab", "<script>", "a;b"}) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader(TraceIdFilter.REQUEST_ID_HEADER, hostile);
            AtomicReference<String> seen = new AtomicReference<>();

            filter.doFilter(req, new MockHttpServletResponse(), capturingChain(seen));

            assertThat(seen.get())
                    .as("header <%s> must not be adopted verbatim", hostile)
                    .isNotEqualTo(hostile);
        }
    }

    @Test
    void anAbsurdlyLongHeaderIsRefused() throws Exception {
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "a".repeat(500));
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seen));

        assertThat(seen.get()).hasSizeLessThan(100);
    }

    @Test
    void aBlankHeaderFallsBackToAGeneratedId() throws Exception {
        request.addHeader(TraceIdFilter.REQUEST_ID_HEADER, "   ");
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, capturingChain(seen));

        assertThat(seen.get()).isNotBlank();
    }

    /**
     * On a pooled executor a thread that keeps its MDC entry stamps the next,
     * unrelated request with the previous request's id — which is worse than
     * having no id at all, because it is confidently wrong.
     */
    @Test
    void theMdcIsClearedAfterTheRequest() throws Exception {
        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
    }

    @Test
    void theMdcIsClearedEvenWhenTheChainThrows() {
        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("downstream failure");
        };

        try {
            filter.doFilter(request, response, exploding);
        } catch (Exception ignored) {
            // The filter must not swallow it; only the cleanup matters here.
        }

        assertThat(MDC.get(TraceIdFilter.TRACE_ID_KEY)).isNull();
    }

    @Test
    void separateRequestsGetSeparateIds() throws Exception {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturingChain(first));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturingChain(second));

        assertThat(first.get()).isNotEqualTo(second.get());
    }
}
