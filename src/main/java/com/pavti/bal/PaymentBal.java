package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.BusinessDateHelper;
import com.pavti.common.TextHelper;
import com.pavti.dal.OrderDal;
import com.pavti.dal.PaymentDal;
import com.pavti.dataobject.order.Order;
import com.pavti.dataobject.order.OrderListItem;
import com.pavti.dataobject.payment.Payment;
import com.pavti.dataobject.payment.PaymentSaveRequest;
import com.pavti.dataobject.payment.RefundRequest;
import com.pavti.filter.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records money the customer paid outside the app (cash in hand, UPI scanner).
 * Pavti does not move money; it only writes down what was received.
 */
@Service
public class PaymentBal {

    private static final Set<String> PAYABLE = Set.of("BILLED", "PART_PAID", "REFUNDED");

    private final PaymentDal paymentDal;
    private final OrderDal orderDal;
    private final OrderBal orderBal;
    private final AuditBal audit;

    public PaymentBal(PaymentDal paymentDal, OrderDal orderDal, OrderBal orderBal, AuditBal audit) {
        this.paymentDal = paymentDal;
        this.orderDal = orderDal;
        this.orderBal = orderBal;
        this.audit = audit;
    }

    public List<Payment> getAll(CurrentUser user) {
        return paymentDal.getAll(user.requireBusinessId(), 1000);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order save(CurrentUser user, PaymentSaveRequest req) {
        long bid = user.requireBusinessId();
        String status = orderDal.lock(req.orderId(), bid).orElseThrow(() -> ApiException.notFound("Bill not found."));
        if (!PAYABLE.contains(status)) throw ApiException.badRequest("Make the bill first, then mark it paid.");
        String mode = clean(req.paymentMode());
        if (!"CASH".equals(mode) && !"UPI".equals(mode)) throw ApiException.badRequest("Choose cash or UPI.");

        OrderListItem o = orderBal.row(bid, req.orderId());
        BigDecimal balance = o.totalAmount().subtract(OrderBal.netPaid(paymentDal.getByOrder(o.orderId()))).max(BigDecimal.ZERO);
        BigDecimal amount = TextHelper.money(req.amount());
        if (amount.signum() <= 0) throw ApiException.badRequest("Enter an amount greater than zero.");
        if (amount.compareTo(balance) > 0) throw ApiException.badRequest("Amount is more than the balance due.");

        BigDecimal cash = "CASH".equals(mode) && req.cashReceived() != null && req.cashReceived().signum() > 0
                ? TextHelper.money(req.cashReceived()) : null;
        if (cash != null && cash.compareTo(amount) < 0) throw ApiException.badRequest("Cash given is less than the amount.");
        BigDecimal change = cash == null ? null : cash.subtract(amount);

        long paymentId = paymentDal.save(bid, o.orderId(), "PAYMENT", mode, amount, cash, change, null, null,
                BusinessDateHelper.today(user.timeZone()), clean(req.requestKey()), user.userId());
        orderBal.refreshMoney(bid, o.orderId(), user.userId());
        audit.log(user, "PAYMENT_RECEIVED", "payments", paymentId, o.billNo() + ": " + mode + " " + amount, null);
        return orderBal.load(bid, o.orderId());
    }

    /** Owner only. Writes a REFUND row; the original payment is never changed or deleted. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void refund(CurrentUser user, RefundRequest req) {
        long bid = user.requireBusinessId();
        String reason = clean(req.reason());
        if (reason.isEmpty()) throw ApiException.badRequest("Write a reason for the refund.");
        if (reason.length() > 255) throw ApiException.badRequest("Reason is too long.");
        Payment p = paymentDal.getById(req.paymentId(), bid).orElseThrow(() -> ApiException.notFound("Payment not found."));
        orderDal.lock(p.orderId(), bid).orElseThrow(() -> ApiException.notFound("Bill not found."));
        p = paymentDal.getById(req.paymentId(), bid).orElseThrow(() -> ApiException.notFound("Payment not found."));
        if (!"PAYMENT".equals(p.paymentType()) || p.isRefunded()) throw ApiException.badRequest("This payment cannot be refunded.");

        paymentDal.save(bid, p.orderId(), "REFUND", p.paymentMode(), p.amount(), null, null, p.paymentId(), reason,
                BusinessDateHelper.today(user.timeZone()), "refund-" + p.paymentId(), user.userId());
        orderBal.refreshMoney(bid, p.orderId(), user.userId());
        audit.log(user, "PAYMENT_REFUND", "payments", p.paymentId(), p.billNo() + ": " + p.amount(), reason);
    }
}
