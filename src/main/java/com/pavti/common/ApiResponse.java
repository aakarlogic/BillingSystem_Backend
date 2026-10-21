package com.pavti.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Every API answer has this shape (same as Health 360's ApiResponse, plus Data):
 * { "Success": true, "StatusCode": 200, "Message": "OK", "Data": ... }
 */
public record ApiResponse<T>(
        @JsonProperty("Success") boolean success,
        @JsonProperty("StatusCode") int statusCode,
        @JsonProperty("Message") String message,
        @JsonProperty("Data") T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, 200, "OK", data);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, 200, message, data);
    }

    public static <T> ApiResponse<T> failure(int statusCode, String message) {
        return new ApiResponse<>(false, statusCode, message, null);
    }
}
