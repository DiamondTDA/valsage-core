
package io.github.xylars.valsage.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RequestGateFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_USERS = 10_000;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private final int maxBodyBytes;
    private final int requestsPerMinute;

    private final ConcurrentHashMap<String, Window> windows =
            new ConcurrentHashMap<>();

    private final Clock clock = Clock.systemUTC();

    public RequestGateFilter(
            @Value("${security-pipeline.max-payload-bytes:8192}")
            int maxBodyBytes,

            @Value("${security-pipeline.requests-per-minute:20}")
            int requestsPerMinute
    ) {
        if (maxBodyBytes <= 0 || requestsPerMinute <= 0) {
            throw new IllegalArgumentException(
                    "Security limits must be positive"
            );
        }

        this.maxBodyBytes = maxBodyBytes;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/v1/audits".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            chain.doFilter(request, response);
            return;
        }

        if (isRateLimited(authentication.getName())) {
            response.setHeader("Retry-After", "60");
            reject(response, 429, "RATE_LIMITED");
            return;
        }

        long contentLength = request.getContentLengthLong();

        if (contentLength > maxBodyBytes) {
            reject(response, 413, "PAYLOAD_TOO_LARGE");
            return;
        }

        // Read at most maxBodyBytes + 1 to prevent
        // chunked requests from bypassing the size limit.
        byte[] body = request.getInputStream()
                .readNBytes(maxBodyBytes + 1);

        if (body.length > maxBodyBytes) {
            reject(response, 413, "PAYLOAD_TOO_LARGE");
            return;
        }

        CachedBodyRequest wrappedRequest =
                new CachedBodyRequest(request, body);

        chain.doFilter(wrappedRequest, response);
    }

    private boolean isRateLimited(String userId) {

        long currentMinute =
                clock.instant().getEpochSecond() / 60;

        // Remove expired windows periodically.
        // Prevent the map from growing indefinitely.
        if (windows.size() >= MAX_TRACKED_USERS) {

            windows.entrySet().removeIf(entry ->
                    entry.getValue().minute < currentMinute
            );
        }

        // Fail closed when the limiter cannot track
        // another user without exceeding its capacity.
        if (!windows.containsKey(userId)
                && windows.size() >= MAX_TRACKED_USERS) {
            return true;
        }

        Window window = windows.compute(
                userId,
                (key, existing) -> {
                    if (existing == null
                            || existing.minute != currentMinute) {
                        return new Window(currentMinute);
                    }

                    return existing;
                }
        );

        return window.hits.incrementAndGet() > requestsPerMinute;
    }

    private static void reject(
            HttpServletResponse response,
            int status,
            String title
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");

        OBJECT_MAPPER.writeValue(
                response.getWriter(),
                Map.of(
                        "title", title,
                        "status", status
                )
        );
    }

    /**
     * Tracks requests for a single user
     * within a fixed one-minute window.
     */
    private static final class Window {

        private final long minute;
        private final AtomicInteger hits =
                new AtomicInteger(0);

        private Window(long minute) {
            this.minute = minute;
        }
    }

    /**
     * Allows the request body to be read again
     * by the controller after the security filter.
     */
    private static final class CachedBodyRequest
            extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(
                HttpServletRequest request,
                byte[] body
        ) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {

            ByteArrayInputStream input =
                    new ByteArrayInputStream(body);

            return new ServletInputStream() {

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(
                        ReadListener listener
                ) {
                    throw new UnsupportedOperationException(
                            "Async body reading is not supported"
                    );
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(
                            getInputStream(),
                            StandardCharsets.UTF_8
                    )
            );
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
