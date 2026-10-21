package com.pavti.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What the server sends back: the normal ApiResponse JSON, encrypted with the same AES key. */
public record EncryptedResponse(
        @JsonProperty("EncryptedData") String encryptedData,
        @JsonProperty("IV") String iv) {}
