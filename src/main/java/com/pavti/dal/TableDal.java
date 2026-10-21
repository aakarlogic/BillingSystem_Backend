package com.pavti.dal;

import com.pavti.dataobject.table.TableCard;
import com.pavti.dataobject.table.TableRow;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Dining tables. */
@Repository
public class TableDal {

    private final DataClass db;

    public TableDal(DataClass db) {
        this.db = db;
    }

    public List<TableCard> getFloor(long businessId) {
        return db.getList("sp_table_get_floor", TableCard.class, businessId);
    }

    public long count(long businessId) {
        return db.getLong("sp_table_count", businessId);
    }

    /** tableName empty = next number ("Table 9"). */
    public long add(long businessId, String tableName, long by) {
        return db.getLong("sp_table_add", businessId, tableName, by);
    }

    public void remove(long tableId, long businessId, long by) {
        db.execute("sp_table_remove", tableId, businessId, by);
    }

    public Optional<TableRow> getById(long tableId, long businessId) {
        return db.getOne("sp_table_get_by_id", TableRow.class, tableId, businessId);
    }

    /** Locks the table row until the transaction ends, so two waiters cannot open the same table. */
    public Optional<TableRow> lock(long tableId, long businessId) {
        return db.getOne("sp_table_lock", TableRow.class, tableId, businessId);
    }

    public void setStatus(long tableId, String status, Long orderId, long by) {
        db.execute("sp_table_set_status", tableId, status, orderId, by);
    }
}
