package com.pavti.dal;

import com.pavti.common.ApiException;
import com.pavti.dataobject.payment.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Money received (and refunded) against bills. */
@Repository
public class PaymentDal {

    private final DataClass db;

    public PaymentDal(DataClass db) {
        this.db = db;
    }

    public List<Payment> getByOrder(long orderId) {
        return db.getList("sp_payment_get_by_order", Payment.class, orderId);
    }

    public List<Payment> getAll(long businessId, int limit) {
        return db.getList("sp_payment_get_all", Payment.class, businessId, limit);
    }

    public Optional<Payment> getById(long paymentId, long businessId) {
        return db.getOne("sp_payment_get_by_id", Payment.class, paymentId, businessId);
    }

    /** requestKey is unique per business, so a double tap can never save twice. */
    public long save(long businessId, long orderId, String paymentType, String paymentMode, BigDecimal amount,
                     BigDecimal cashReceived, BigDecimal changeReturned, Long refundOfPaymentId, String reason,
                     LocalDate businessDate, String requestKey, long by) {
        try {
            return db.getLong("sp_payment_save", businessId, orderId, paymentType, paymentMode, amount, cashReceived,
                    changeReturned, refundOfPaymentId, reason, businessDate, requestKey, by);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("This payment is already saved.");
        }
    }
}
