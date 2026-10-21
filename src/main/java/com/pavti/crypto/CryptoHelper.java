package com.pavti.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/**
 * The two ciphers used for API encryption (Health 360: Cryptohelper.cs).
 *  - RSA-OAEP with SHA-256: unwraps the one-time AES key the browser made
 *  - AES-256-GCM: encrypts the JSON body. GCM also detects any change to the data
 *    (Health 360 used AES-CBC, which does not).
 * Both match the browser's WebCrypto settings in pavti/src/utils/crypto.
 */
public final class CryptoHelper {

    public static final int AES_KEY_BYTES = 32;
    public static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    // WebCrypto RSA-OAEP {hash: SHA-256} uses SHA-256 for MGF1 too; Java's default would be SHA-1.
    private static final OAEPParameterSpec OAEP_SHA256 =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private CryptoHelper() {}

    public static SecretKey unwrapAesKey(byte[] encryptedKey, PrivateKey privateKey) throws GeneralSecurityException {
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
        byte[] raw = rsa.doFinal(encryptedKey);
        if (raw.length != AES_KEY_BYTES) throw new GeneralSecurityException("AES key must be 256 bits");
        return new SecretKeySpec(raw, "AES");
    }

    /** aad = extra text that must match on both sides (we use the API path), not secret. */
    public static byte[] aesDecrypt(byte[] cipherText, SecretKey key, byte[] iv, String aad) throws GeneralSecurityException {
        if (iv.length != IV_BYTES) throw new GeneralSecurityException("IV must be 12 bytes");
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        aes.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return aes.doFinal(cipherText);
    }

    public static byte[] aesEncrypt(byte[] plain, SecretKey key, byte[] iv, String aad) throws GeneralSecurityException {
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        aes.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return aes.doFinal(plain);
    }

    /** A new random IV for every message; never reuse one with the same key. */
    public static byte[] newIv() {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        return iv;
    }

    /** Extra text bound into GCM, so an encrypted body cannot be replayed against another API. */
    public static String requestAad(String endpoint) {
        return "pavti-request:" + endpoint;
    }

    public static String responseAad(String endpoint) {
        return "pavti-response:" + endpoint;
    }
}
