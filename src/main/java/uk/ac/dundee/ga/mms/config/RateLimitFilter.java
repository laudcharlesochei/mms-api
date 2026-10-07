package uk.ac.dundee.ga.mms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple fixed-window rate limiter per client IP (Section 9.3): sign-in endpoints and write
 * requests. In-memory per dyno, which is adequate for the expected 10 concurrent users.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final MmsProperties props;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(MmsProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        String method = req.getMethod();
        String ip = clientIp(req);
        int limit = 0;
        String bucket = null;
        if (path.startsWith("/api/v1/auth/login") || path.startsWith("/api/v1/auth/invites")) {
            limit = props.getRateLimit().getLoginPerMinute();
            bucket = "login:" + ip;
        } else if (path.startsWith("/api/") && !"GET".equals(method) && !"OPTIONS".equals(method) && !"HEAD".equals(method)) {
            limit = props.getRateLimit().getWritesPerMinute();
            bucket = "write:" + ip;
        }
        if (bucket != null && limit > 0 && !allow(bucket, limit)) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded. Try again in a minute.\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private boolean allow(String key, int limit) {
        long minute = System.currentTimeMillis() / 60_000;
        Window w = windows.compute(key, (k, old) -> old == null || old.minute != minute ? new Window(minute) : old);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute != minute);
        }
        return w.count.incrementAndGet() <= limit;
    }

    static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String[] parts = fwd.split(",");
            return parts[parts.length - 1].trim(); // Heroku router appends the real client IP last
        }
        return req.getRemoteAddr();
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
