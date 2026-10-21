package com.pavti.crypto;

import com.pavti.common.ApiException;
import com.pavti.config.AppProperties;
import com.pavti.filter.ValidateEncryptedRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Checks @ValidateEncryptedRequest before the controller runs (Health 360:
 * ValidateEncryptedRequestAttribute). The body was already opened by ApiCryptoFilter, which
 * marks the request; here we only refuse a plain call when encryption is required.
 *
 * pavti.crypto.mode: required = refuse plain calls to marked APIs (production),
 * optional = allow them too (Swagger / Postman while developing), off = no encryption.
 */
@Component
public class EncryptedRequestInterceptor implements HandlerInterceptor {

    private final boolean required;

    public EncryptedRequestInterceptor(AppProperties props) {
        this.required = "required".equals(props.crypto().mode());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!required || !(handler instanceof HandlerMethod method)) return true;
        boolean marked = method.hasMethodAnnotation(ValidateEncryptedRequest.class)
                || method.getBeanType().isAnnotationPresent(ValidateEncryptedRequest.class);
        if (marked && request.getAttribute(ApiCryptoFilter.DECRYPTED) == null) {
            throw ApiException.badRequest("This server only accepts encrypted requests.");
        }
        return true;
    }
}
