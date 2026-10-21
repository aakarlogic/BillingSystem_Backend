package com.pavti.dal;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps a stored-procedure row onto a Java record by name, like Health 360's
 * getConvertedData: column "bill_no" fills record field "billNo" (case and
 * underscores ignored). Columns without a matching field are skipped; fields
 * without a column get null / 0 / false.
 *
 * Dates: DATETIME → ISO text in UTC ("2026-10-20T09:15:00Z"), DATE → "2026-10-20".
 */
public class RecordRowMapper<T extends Record> implements RowMapper<T> {

    private final Class<T> type;
    private final RecordComponent[] components;
    private final Constructor<T> constructor;
    private int[] columnIndex; // per component, built on first row

    public RecordRowMapper(Class<T> type) {
        this.type = type;
        this.components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) types[i] = components[i].getType();
        try {
            this.constructor = type.getDeclaredConstructor(types);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No canonical constructor on " + type.getName(), e);
        }
    }

    private static String norm(String name) {
        return name.replace("_", "").toLowerCase();
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        if (columnIndex == null) {
            Map<String, Integer> byName = new HashMap<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) byName.putIfAbsent(norm(meta.getColumnLabel(c)), c);
            columnIndex = new int[components.length];
            for (int i = 0; i < components.length; i++) columnIndex[i] = byName.getOrDefault(norm(components[i].getName()), 0);
        }
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            args[i] = read(rs, meta, columnIndex[i], components[i].getType());
        }
        try {
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build " + type.getSimpleName(), e);
        }
    }

    private static Object read(ResultSet rs, ResultSetMetaData meta, int col, Class<?> t) throws SQLException {
        if (col == 0) return defaultFor(t);
        if (t == String.class) {
            int sqlType = meta.getColumnType(col);
            if (sqlType == Types.TIMESTAMP || sqlType == Types.TIMESTAMP_WITH_TIMEZONE) {
                Timestamp ts = rs.getTimestamp(col);
                return ts == null ? null : ts.toInstant().toString();
            }
            if (sqlType == Types.DATE) {
                java.sql.Date d = rs.getDate(col);
                return d == null ? null : d.toLocalDate().toString();
            }
            return rs.getString(col);
        }
        if (t == Long.class || t == long.class) {
            long v = rs.getLong(col);
            return rs.wasNull() ? defaultFor(t) : v;
        }
        if (t == Integer.class || t == int.class) {
            int v = rs.getInt(col);
            return rs.wasNull() ? defaultFor(t) : v;
        }
        if (t == Boolean.class || t == boolean.class) {
            boolean v = rs.getBoolean(col);
            return rs.wasNull() ? defaultFor(t) : v;
        }
        if (t == BigDecimal.class) {
            BigDecimal v = rs.getBigDecimal(col);
            return v; // null stays null (e.g. cash_received for UPI)
        }
        if (t == Double.class || t == double.class) {
            double v = rs.getDouble(col);
            return rs.wasNull() ? defaultFor(t) : v;
        }
        if (t == LocalDate.class) {
            java.sql.Date d = rs.getDate(col);
            return d == null ? null : d.toLocalDate();
        }
        if (t == java.time.Instant.class) {
            Timestamp ts = rs.getTimestamp(col);
            return ts == null ? null : ts.toInstant();
        }
        return rs.getObject(col);
    }

    private static Object defaultFor(Class<?> t) {
        if (t == long.class) return 0L;
        if (t == int.class) return 0;
        if (t == boolean.class) return false;
        if (t == double.class) return 0d;
        return null;
    }
}
