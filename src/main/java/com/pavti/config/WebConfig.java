package com.pavti.config;

import com.pavti.crypto.ApiCryptoFilter;
import com.pavti.crypto.EncryptedRequestInterceptor;
import com.pavti.filter.CurrentUserArgumentResolver;
import com.pavti.filter.TokenInterceptor;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the React app (first filter) + the token interceptor + CurrentUser parameters. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final TokenInterceptor tokenInterceptor;
    private final EncryptedRequestInterceptor encryptedRequestInterceptor;
    private final CurrentUserArgumentResolver currentUserResolver;

    public WebConfig(AppProperties props, TokenInterceptor tokenInterceptor, EncryptedRequestInterceptor encryptedRequestInterceptor,
                     CurrentUserArgumentResolver currentUserResolver) {
        this.props = props;
        this.tokenInterceptor = tokenInterceptor;
        this.encryptedRequestInterceptor = encryptedRequestInterceptor;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * CORS runs as the very first filter (before ApiCryptoFilter), so even answers that filter
     * writes itself (key changed, not encrypted) carry the CORS headers the browser needs.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", ApiCryptoFilter.HEADER));
        cors.setExposedHeaders(List.of(ApiCryptoFilter.HEADER));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. @ValidateEncryptedRequest  2. @ValidateToken / @RequirePage / @RequirePermission / @RequireRole
        registry.addInterceptor(encryptedRequestInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(tokenInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
