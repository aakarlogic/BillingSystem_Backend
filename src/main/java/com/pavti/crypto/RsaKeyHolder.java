package com.pavti.crypto;

import com.pavti.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The server's RSA key pair. The private key is never in the code (Health 360 had it hard-coded):
 *  1. PAVTI_RSA_PRIVATE_KEY (PEM text) — use this on a server
 *  2. else the key file (default keys/pavti_rsa_private.pem) — created by itself on first start,
 *     so restarts keep the same key. The keys/ folder is in .gitignore.
 */
@Component
public class RsaKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyHolder.class);

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;

    public RsaKeyHolder(AppProperties props) throws Exception {
        AppProperties.Crypto c = props.crypto();
        String pem = c.privateKey();
        if (pem == null || pem.isBlank()) pem = loadOrCreate(Path.of(c.keyFile()));
        this.privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(fromPem(pem)));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
        this.publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
        this.keyId = HexFormat.of().formatHex(hash, 0, 8);
        log.info("API encryption key ready (keyId {}, mode {})", keyId, c.mode());
    }

    private static String loadOrCreate(Path file) throws Exception {
        if (Files.exists(file)) return Files.readString(file, StandardCharsets.UTF_8);
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, pem, StandardCharsets.UTF_8);
            log.info("Created a new API encryption key at {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not save the API key to {} ({}); a new key is made on every start.", file, e.getMessage());
        }
        return pem;
    }

    private static byte[] fromPem(String pem) {
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("Use a PKCS#8 key (BEGIN PRIVATE KEY). Convert: openssl pkcs8 -topk8 -nocrypt -in old.pem");
        }
        String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    /** X.509 / SPKI, base64 — the format WebCrypto importKey("spki") reads. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** Short fingerprint; the browser sends it so we can tell it when the key has changed. */
    public String keyId() {
        return keyId;
    }
}
