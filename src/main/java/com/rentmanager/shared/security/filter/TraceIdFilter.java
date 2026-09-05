package com.rentmanager.shared.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a trace id, in the log MDC and on the response.
 *
 * <h2>Why this was missing and why it mattered</h2>
 * {@code ErrorTrackingService} has always read {@code MDC.get("traceId")} when
 * persisting an {@code ErrorEvent}. Nothing in the codebase ever called
 * {@code MDC.put}, so that read returned null on every request and every row
 * in {@code error_events} was written with no trace id at all.
 *
 * <p>The consequence is subtle and expensive: the error table filled up
 * correctly, and each row was individually useless. An exception could not be
 * tied to the request that caused it, to the log lines emitted alongside it,
 * or to the other errors from the same failing operation. Debugging a
 * production incident meant correlating on timestamps.
 *
 * <h2>Accepting an inbound id</h2>
 * If the caller sends {@code X-Request-Id} it is adopted rather than replaced,
 * so a trace started at the frontend or a load balancer continues through this
 * service instead of restarting. The value is sanitised first: it lands in log
 * lines, and an unvalidated header in a log file is how log injection works.
 *
 * <h2>Ordering and cleanup</h2>
 * Runs at {@code HIGHEST_PRECEDENCE + 1} — immediately after
 * {@code TenantContextCleanupFilter} — so that essentially every log line
 * produced during a request carries the id, including those from the security
 * chain. The {@code finally} block is load-bearing for the same reason that
 * filter's is: on a pooled executor a thread that keeps its MDC entry will
 * stamp the next, unrelated request with the previous request's id, which is
 * worse than having none.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Long enough to be unique, short enough to read out over the phone. */
    private static final int GENERATED_LENGTH = 16;
    private static final int MAX_INBOUND_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String traceId = resolveTraceId(request);

        MDC.put(TRACE_ID_KEY, traceId);

        // Echoed back so a user reporting a problem can quote a reference
        // that actually finds the request, and so a frontend can attach it to
        // its own error reports.
        response.setHeader(REQUEST_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(REQUEST_ID_HEADER);
        String sanitised = sanitise(inbound);
        return sanitised != null ? sanitised : generate();
    }

    /**
     * Accepts only characters that are safe in a log line and a header value.
     * Anything else — newlines above all, which would let a caller forge log
     * entries — causes the header to be ignored and a fresh id generated.
     */
    private static String sanitise(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_INBOUND_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!allowed) {
                return null;
            }
        }
        return trimmed;
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, GENERATED_LENGTH);
    }
}
