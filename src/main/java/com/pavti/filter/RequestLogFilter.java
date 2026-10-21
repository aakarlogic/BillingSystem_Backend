package com.pavti.filter;

import com.pavti.dal.LoggerDal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

/**
 * Stores every POST /api call in api_request_logs (Health 360: LoggerBAL SAVE_REQUEST /
 * SAVE_RESPONSE). Passwords, OTPs and tokens are replaced with "***" before saving.
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {

    private static final String SECRET_FIELDS =
            "(\"(?:password|newPassword|currentPassword|ownerPassword|otp|refreshToken)\"\\s*:\\s*)\"[^\"]*\"";

    private final LoggerDal loggerDal;

    public RequestLogFilter(LoggerDal loggerDal) {
        this.loggerDal = loggerDal;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrapped, response);
        } finally {
            String body = new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8)
                    .replaceAll(SECRET_FIELDS, "$1\"***\"");
            if (body.length() > 4000) body = body.substring(0, 4000);
            CurrentUser user = (CurrentUser) request.getAttribute(TokenInterceptor.CURRENT_USER);
            loggerDal.logRequest(request.getRequestURI(), body, response.getStatus(),
                    (int) (System.currentTimeMillis() - start),
                    user != null ? user.userId() : null, user != null ? user.businessId() : null, request.getRemoteAddr());
        }
    }
}
