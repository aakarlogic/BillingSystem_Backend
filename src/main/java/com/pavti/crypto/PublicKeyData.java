package com.pavti.crypto;

/** Answer of /api/Crypto/GetPublicKey. */
public record PublicKeyData(String keyId, String publicKey, String algorithm, String mode) {}
