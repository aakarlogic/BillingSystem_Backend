package com.pavti.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "pavti:" in application.yml. */
@ConfigurationProperties(prefix = "pavti")
public record AppProperties(Jwt jwt, Otp otp, Cors cors, Bootstrap bootstrap, Crypto crypto, boolean demoData) {

    public record Jwt(String secret, String issuer, int accessTokenMinutes, int refreshTokenDays) {}

    public record Otp(int minutesValid, int maxAttempts, boolean devMode) {}

    public record Cors(List<String> allowedOrigins) {}

    /** API body encryption (see com.pavti.crypto). mode: off | optional | required. */
    public record Crypto(String mode, String privateKey, String keyFile) {}

    public record Bootstrap(String adminMobile, String adminName, String adminPassword) {}
}
