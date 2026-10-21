package com.pavti.crypto;

import com.pavti.common.ApiResponse;
import com.pavti.config.AppProperties;
import com.pavti.controller.BaseApiController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gives the browser the public key. Public and never encrypted (it is needed to start), so it
 * deliberately has no @ValidateEncryptedRequest.
 */
@RestController
@RequestMapping("/api/Crypto")
public class CryptoController extends BaseApiController {

    private final RsaKeyHolder keys;
    private final AppProperties props;

    public CryptoController(RsaKeyHolder keys, AppProperties props) {
        this.keys = keys;
        this.props = props;
    }

    @PostMapping("/GetPublicKey")
    public ApiResponse<PublicKeyData> getPublicKey() {
        return ok(new PublicKeyData(keys.keyId(), keys.publicKeyBase64(), "RSA-OAEP-SHA256 + AES-256-GCM", props.crypto().mode()));
    }
}
