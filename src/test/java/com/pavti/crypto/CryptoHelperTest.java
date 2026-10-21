package com.pavti.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Does what the browser does (wrap key, encrypt), then checks the server side opens it. */
class CryptoHelperTest {

    private static KeyPair rsa;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        rsa = gen.generateKeyPair();
    }

    private static SecretKey newAesKey() throws Exception {
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256);
        return gen.generateKey();
    }

    private static byte[] wrapLikeBrowser(SecretKey key) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.ENCRYPT_MODE, rsa.getPublic(),
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return c.doFinal(key.getEncoded());
    }

    @Test
    void roundTripWithMarathiText() throws Exception {
        SecretKey key = newAesKey();
        SecretKey unwrapped = CryptoHelper.unwrapAesKey(wrapLikeBrowser(key), rsa.getPrivate());
        assertArrayEquals(key.getEncoded(), unwrapped.getEncoded());

        byte[] plain = "{\"itemName\":\"मसाला डोसा\",\"price\":180}".getBytes(StandardCharsets.UTF_8);
        byte[] iv = CryptoHelper.newIv();
        byte[] cipher = CryptoHelper.aesEncrypt(plain, key, iv, CryptoHelper.requestAad("MenuItem/Save"));
        assertArrayEquals(plain, CryptoHelper.aesDecrypt(cipher, unwrapped, iv, CryptoHelper.requestAad("MenuItem/Save")));
    }

    @Test
    void changedDataIsRejected() throws Exception {
        SecretKey key = newAesKey();
        byte[] iv = CryptoHelper.newIv();
        byte[] cipher = CryptoHelper.aesEncrypt("{\"amount\":100}".getBytes(StandardCharsets.UTF_8), key, iv, "pavti-request:Payment/Save");
        cipher[3] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> CryptoHelper.aesDecrypt(cipher, key, iv, "pavti-request:Payment/Save"));
    }

    @Test
    void bodyForOneApiCannotBeSentToAnother() throws Exception {
        SecretKey key = newAesKey();
        byte[] iv = CryptoHelper.newIv();
        byte[] cipher = CryptoHelper.aesEncrypt("{\"id\":5}".getBytes(StandardCharsets.UTF_8), key, iv, CryptoHelper.requestAad("Order/GetById"));
        assertThrows(GeneralSecurityException.class, () -> CryptoHelper.aesDecrypt(cipher, key, iv, CryptoHelper.requestAad("Order/Cancel")));
    }
}
