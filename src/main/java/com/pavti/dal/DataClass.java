package com.pavti.dal;

import com.pavti.common.ApiException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs MySQL stored procedures, like Health 360's DataClass.getreaderFromSPWithParm.
 * Parameters are passed in the order the procedure declares them (p_...).
 *
 * A procedure can stop with SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '...';
 * that message becomes a 400 error the user sees.
 */
@Component
public class DataClass {

    private final JdbcTemplate jdbc;

    public DataClass(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String call(String procedure, int paramCount) {
        StringBuilder sb = new StringBuilder("CALL ").append(procedure).append('(');
        for (int i = 0; i < paramCount; i++) sb.append(i == 0 ? "?" : ", ?");
        return sb.append(')').toString();
    }

    /** Procedure that returns rows → list of records. */
    public <T extends Record> List<T> getList(String procedure, Class<T> type, Object... params) {
        try {
            return jdbc.query(call(procedure, params.length), new RecordRowMapper<>(type), params);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /** Procedure that returns at most one row. */
    public <T extends Record> Optional<T> getOne(String procedure, Class<T> type, Object... params) {
        List<T> rows = getList(procedure, type, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** First column of the first row as a number (counts, new ids). */
    public long getLong(String procedure, Object... params) {
        try {
            List<Long> rows = jdbc.query(call(procedure, params.length), (rs, n) -> rs.getLong(1), params);
            return rows.isEmpty() ? 0L : rows.get(0);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /** First column of the first row as text. */
    public Optional<String> getString(String procedure, Object... params) {
        try {
            List<String> rows = jdbc.query(call(procedure, params.length), (rs, n) -> rs.getString(1), params);
            return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /** First column of every row as text (e.g. permission codes). */
    public List<String> getStrings(String procedure, Object... params) {
        try {
            return jdbc.query(call(procedure, params.length), (rs, n) -> rs.getString(1), params);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /** Procedure that only writes (no rows back). */
    public void execute(String procedure, Object... params) {
        try {
            jdbc.update(call(procedure, params.length), params);
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /** SIGNAL '45000' from a procedure → ApiException(400, its message). Anything else stays as is. */
    private static RuntimeException translate(DataAccessException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SQLException sql && "45000".equals(sql.getSQLState())) {
                return ApiException.badRequest(sql.getMessage());
            }
            t = t.getCause();
        }
        return e;
    }
}
