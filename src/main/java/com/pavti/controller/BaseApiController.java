package com.pavti.controller;

import com.pavti.common.ApiResponse;

/**
 * Base for all controllers (Health 360: BaseApiController).
 * Errors are not handled here: throw ApiException and GlobalExceptionHandler answers.
 */
public abstract class BaseApiController {

    protected <T> ApiResponse<T> ok(T data) {
        return ApiResponse.ok(data);
    }

    protected <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.ok(data, message);
    }
}
