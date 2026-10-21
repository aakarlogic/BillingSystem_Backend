package com.pavti.dal;

import com.pavti.dataobject.order.OrderItem;
import com.pavti.dataobject.order.OrderListItem;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Orders (bills) and their lines. The bill math itself is in BillingCalculator. */
@Repository
public class OrderDal {

    private final DataClass db;

    public OrderDal(DataClass db) {
        this.db = db;
    }

    /** mineUserId null = everyone's orders. */
    public List<OrderListItem> getAll(long businessId, String status, String search, Long mineUserId) {
        return db.getList("sp_order_get_all", OrderListItem.class, businessId, status, search, mineUserId);
    }

    public Optional<OrderListItem> getById(long orderId, long businessId) {
        return db.getOne("sp_order_get_by_id", OrderListItem.class, orderId, businessId);
    }

    /** Locks the order row until the transaction ends (call first). Empty = not found. Returns its status. */
    public Optional<String> lock(long orderId, long businessId) {
        return db.getString("sp_order_lock", orderId, businessId);
    }

    public List<OrderItem> getItems(long orderId) {
        return db.getList("sp_order_items_get", OrderItem.class, orderId);
    }

    public record Header(Long orderId, long businessId, String orderType, Long tableId, Long customerId, String customerName,
                         LocalDate businessDate, BigDecimal subTotal, String discountType, BigDecimal discountValue,
                         BigDecimal discountAmount, String discountReason, BigDecimal taxableAmount, BigDecimal taxAmount,
                         BigDecimal serviceChargePct, BigDecimal serviceChargeAmount, BigDecimal totalAmount, String notes) {}

    /** Insert (orderId null) or update an OPEN order. Returns the order id. */
    public long saveHeader(Header h, long by) {
        return db.getLong("sp_order_save_header", h.orderId(), h.businessId(), h.orderType(), h.tableId(), h.customerId(),
                h.customerName(), h.businessDate(), h.subTotal(), h.discountType(), h.discountValue(), h.discountAmount(),
                h.discountReason(), h.taxableAmount(), h.taxAmount(), h.serviceChargePct(), h.serviceChargeAmount(),
                h.totalAmount(), h.notes(), by);
    }

    public void clearItems(long orderId, long by) {
        db.execute("sp_order_items_clear", orderId, by);
    }

    public void addItem(long businessId, long orderId, Long itemId, String itemName, String unitName, BigDecimal quantity,
                        BigDecimal unitPrice, BigDecimal lineAmount, BigDecimal discountAmount, BigDecimal taxRate,
                        BigDecimal taxAmount, BigDecimal lineTotal, long by) {
        db.execute("sp_order_item_add", businessId, orderId, itemId, itemName, unitName, quantity, unitPrice, lineAmount,
                discountAmount, taxRate, taxAmount, lineTotal, by);
    }

    /** Gives the next bill number. Must run inside @Transactional (the procedure locks rows). */
    public String makeBill(long orderId, long businessId, long by) {
        return db.getString("sp_order_make_bill", orderId, businessId, by).orElseThrow();
    }

    public void cancel(long orderId, long businessId, String reason, long by) {
        db.execute("sp_order_cancel", orderId, businessId, reason, by);
    }

    public void updateMoney(long orderId, BigDecimal paid, BigDecimal balance, String status, long by) {
        db.execute("sp_order_update_money", orderId, paid, balance, status, by);
    }
}
