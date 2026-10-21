package com.pavti.dal;

import com.pavti.dataobject.order.OrderListItem;
import com.pavti.dataobject.payment.Payment;
import com.pavti.dataobject.report.ItemSalesRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Raw rows for the dashboard and sales report; BAL adds them up. */
@Repository
public class ReportDal {

    private final DataClass db;

    public ReportDal(DataClass db) {
        this.db = db;
    }

    public List<OrderListItem> orders(long businessId, LocalDate from, LocalDate to) {
        return db.getList("sp_report_orders", OrderListItem.class, businessId, from, to);
    }

    /** mineUserId null = all staff. */
    public List<ItemSalesRow> items(long businessId, LocalDate from, LocalDate to, Long mineUserId) {
        return db.getList("sp_report_items", ItemSalesRow.class, businessId, from, to, mineUserId);
    }

    public List<Payment> payments(long businessId, LocalDate from, LocalDate to) {
        return db.getList("sp_report_payments", Payment.class, businessId, from, to);
    }

    public List<OrderListItem> unpaid(long businessId) {
        return db.getList("sp_report_unpaid", OrderListItem.class, businessId);
    }
}
