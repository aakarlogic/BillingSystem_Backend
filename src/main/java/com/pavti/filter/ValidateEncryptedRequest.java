package com.pavti.filter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Body must arrive encrypted (same as Health 360's [ValidateEncryptedRequest]).
 * Put it on a controller class (all its APIs) or on one method, next to @ValidateToken.
 *
 * ApiCryptoFilter does the actual decrypting/encrypting; EncryptedRequestInterceptor refuses
 * a plain call to a method marked with this when pavti.crypto.mode = required.
 * Every controller must have it — ControllerEncryptionTest fails the build if one does not.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateEncryptedRequest {}
