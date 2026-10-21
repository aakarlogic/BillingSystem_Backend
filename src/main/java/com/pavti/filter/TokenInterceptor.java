package com.pavti.filter;

import com.pavti.bal.RightsBal;
import com.pavti.common.ApiException;
import com.pavti.common.JwtTokenHelper;
import com.pavti.dal.AuthDal;
import com.pavti.dal.SessionDal;
import com.pavti.dataobject.auth.SessionUserRow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs before every controller method (Health 360: ValidateTokenAttribute).
 * 1. @ValidateToken / @RequirePage / @RequirePermission / @RequireRole → the token must be
 *    valid, the session not signed out, the person active and their business not paused.
 * 2. Then the page right, staff tick or role is checked.
 * The signed-in person is stored on the request as CurrentUser.
 */
@Component
public class TokenInterceptor implements HandlerInterceptor {

    public static final String CURRENT_USER = "pavti.currentUser";

    private final JwtTokenHelper jwt;
    private final SessionDal sessionDal;
    private final AuthDal authDal;
    private final RightsBal rights;

    public TokenInterceptor(JwtTokenHelper jwt, SessionDal sessionDal, AuthDal authDal, RightsBal rights) {
        this.jwt = jwt;
        this.sessionDal = sessionDal;
        this.authDal = authDal;
        this.rights = rights;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;

        RequirePage page = method.getMethodAnnotation(RequirePage.class);
        RequirePermission permission = method.getMethodAnnotation(RequirePermission.class);
        RequireRole role = method.getMethodAnnotation(RequireRole.class);
        boolean needsToken = page != null || permission != null || role != null
                || method.hasMethodAnnotation(ValidateToken.class)
                || method.getBeanType().isAnnotationPresent(ValidateToken.class);
        if (!needsToken) return true;

        CurrentUser user = authenticate(request.getHeader("Authorization"));
        request.setAttribute(CURRENT_USER, user);

        if (role != null && Arrays.stream(role.value()).noneMatch(r -> r.equals(user.roleCode()))) {
            throw ApiException.forbidden("You do not have permission to do this.");
        }
        if (page != null) rights.requirePage(user, page.page(), page.action());
        if (permission != null) rights.requirePermission(user, permission.value());
        return true;
    }

    private CurrentUser authenticate(String authorizationHeader) {
        JwtTokenHelper.TokenClaims claims = jwt.read(authorizationHeader);
        if (!sessionDal.isActive(claims.sessionId(), claims.userId())) {
            throw ApiException.unauthorized("Your session has ended. Please sign in again.");
        }
        SessionUserRow u = authDal.getSessionUser(claims.userId())
                .orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
        if (!u.isActive()) throw ApiException.unauthorized("Your login is turned off. Ask your owner.");
        if (u.businessId() != null && !"ACTIVE".equals(u.businessStatus())) {
            throw ApiException.unauthorized("This business is paused. Contact support.");
        }
        return new CurrentUser(u.userId(), u.businessId(), u.roleCode(), u.fullName(), claims.sessionId(), u.timeZone(),
                u.currencyCode());
    }
}
