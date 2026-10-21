package com.pavti.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavti.common.ApiResponse;
import com.pavti.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Decrypts every encrypted POST /api/... body before the controller sees it, and encrypts the
 * answer with the same one-time AES key (Health 360: DecryptEncryptedRequestAttribute +
 * ValidateEncryptedRequestAttribute). Controllers, BAL and DAL never know about encryption.
 *
 * A request is encrypted when it has the header X-Pavti-Encrypted: 1. Plain requests pass through;
 * whether a plain call is allowed is decided per API by @ValidateEncryptedRequest
 * (EncryptedRequestInterceptor). pavti.crypto.mode = off switches all of this off.
 *
 * Runs before RequestLogFilter, so the request log stores the plain body with secrets masked.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class ApiCryptoFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Pavti-Encrypted";
    /** Data value of a 409 answer when the browser used an old key; it fetches the new one and retries. */
    public static final String KEY_CHANGED = "KEY_CHANGED";
    /** Request attribute set when the body arrived encrypted and was opened here. */
    public static final String DECRYPTED = "pavti.decrypted";
    private static final String API = "/api/";
    private static final String PUBLIC_KEY_PATH = "Crypto/GetPublicKey";
    private static final Logger log = LoggerFactory.getLogger(ApiCryptoFilter.class);

    private final RsaKeyHolder keys;
    private final ObjectMapper json;
    private final String mode;

    public ApiCryptoFilter(RsaKeyHolder keys, ObjectMapper json, AppProperties props) {
        this.keys = keys;
        this.json = json;
        this.mode = props.crypto().mode();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "off".equals(mode) || !"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith(API)
                || endpoint(request).equals(PUBLIC_KEY_PATH);
    }

    /** "/api/Order/Save" → "Order/Save"; bound into the encryption so bodies cannot be swapped between APIs. */
    private static String endpoint(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.substring(uri.indexOf(API) + API.length()).replaceAll("/+$", "");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"1".equals(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }

        String endpoint = endpoint(request);
        SecretKey aesKey;
        byte[] plainBody;
        try {
            EncryptedRequest env = json.readValue(request.getInputStream(), EncryptedRequest.class);
            if (env == null || env.encryptedAesKey() == null || env.encryptedData() == null || env.iv() == null) {
                writePlain(response, 400, ApiResponse.failure(400, "The encrypted request is incomplete."));
                return;
            }
            if (env.keyId() != null && !env.keyId().equals(keys.keyId())) {
                writePlain(response, 409, new ApiResponse<>(false, 409, "The server key changed. Please try again.", KEY_CHANGED));
                return;
            }
            Base64.Decoder b64 = Base64.getDecoder();
            aesKey = CryptoHelper.unwrapAesKey(b64.decode(env.encryptedAesKey()), keys.privateKey());
            plainBody = CryptoHelper.aesDecrypt(b64.decode(env.encryptedData()), aesKey, b64.decode(env.iv()),
                    CryptoHelper.requestAad(endpoint));
        } catch (GeneralSecurityException | IllegalArgumentException | IOException e) {
            log.warn("Could not decrypt {}: {}", endpoint, e.getMessage());
            writePlain(response, 400, ApiResponse.failure(400, "Could not read the encrypted request."));
            return;
        }

        request.setAttribute(DECRYPTED, Boolean.TRUE);
        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        chain.doFilter(new PlainBodyRequest(request, plainBody), captured);

        // encrypt whatever the app answered (success or error) with the same key and a new IV
        byte[] answer = captured.getContentAsByteArray();
        byte[] iv = CryptoHelper.newIv();
        byte[] out;
        try {
            byte[] cipher = CryptoHelper.aesEncrypt(answer, aesKey, iv, CryptoHelper.responseAad(endpoint));
            Base64.Encoder b64 = Base64.getEncoder();
            out = json.writeValueAsBytes(new EncryptedResponse(b64.encodeToString(cipher), b64.encodeToString(iv)));
        } catch (GeneralSecurityException e) {
            throw new ServletException("Could not encrypt the response", e);
        }
        captured.resetBuffer();
        response.setHeader(HEADER, "1");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(out.length);
        response.getOutputStream().write(out);
        response.flushBuffer();
    }

    private void writePlain(HttpServletResponse response, int status, ApiResponse<?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), body);
    }

    /** The original request, but its body is now the decrypted JSON. */
    private static final class PlainBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        PlainBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getContentType() {
            return MediaType.APPLICATION_JSON_VALUE;
        }

        @Override
        public String getHeader(String name) {
            if ("Content-Length".equalsIgnoreCase(name)) return String.valueOf(body.length);
            return super.getHeader(name);
        }
    }
}
