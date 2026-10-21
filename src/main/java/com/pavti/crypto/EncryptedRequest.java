package com.pavti.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * What the browser sends instead of the plain JSON body (same names as Health 360).
 * All values are base64.
 */
public record EncryptedRequest(
        @JsonProperty("EncryptedAESKey") String encryptedAesKey,
        @JsonProperty("EncryptedData") String encryptedData,
        @JsonProperty("IV") String iv,
        @JsonProperty("KeyId") String keyId) {}
