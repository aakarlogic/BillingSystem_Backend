package com.pavti.dal;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The two technical logs from Health 360: error_logs and api_request_logs.
 * Logging must never break the request, so failures here are only printed.
 */
@Component
public class LoggerDal {

    private static final Logger log = LoggerFactory.getLogger(LoggerDal.class);
    private final JdbcTemplate jdbc;

    public LoggerDal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void storeErrorLog(String className, String methodName, String message, Throwable error, Long userId) {
        try {
            String stack = null;
            if (error != null) {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                stack = sw.toString();
            }
            jdbc.update("CALL sp_store_error_log(?, ?, ?, ?, ?, ?)",
                    className, methodName, message, error != null ? String.valueOf(error.getMessage()) : null, stack, userId);
        } catch (Exception e) {
            log.warn("Could not write error_logs: {}", e.getMessage());
        }
    }

    public void logRequest(String apiUrl, String requestJson, int statusCode, int durationMs, Long userId, Long businessId, String ip) {
        try {
            jdbc.update("CALL sp_log_request(?, ?, ?, ?, ?, ?, ?)", apiUrl, requestJson, statusCode, durationMs, userId, businessId, ip);
        } catch (Exception e) {
            log.warn("Could not write api_request_logs: {}", e.getMessage());
        }
    }
}
