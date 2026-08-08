package com.dbfleetops.operation.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class OperationTaskResultFingerprint {
    public String success(String resultPayloadJson) {
        return sha256("SUCCESS\u001f" + valueOrEmpty(resultPayloadJson));
    }

    public String failure(String errorCode, String errorMessage) {
        return sha256("FAILURE\u001f" + valueOrEmpty(errorCode) + "\u001f"
                + valueOrEmpty(errorMessage));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
