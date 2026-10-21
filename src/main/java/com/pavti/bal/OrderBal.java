package com.pavti.bal;

import static com.pavti.common.TextHelper.clean;

import com.pavti.common.ApiException;
import com.pavti.common.BusinessDateHelper;
import com.pavti.common.TextHelper;
import com.pavti.dal.BusinessDal;
import com.pavti.dal.CustomerDal;
import com.pavti.dal.MenuItemDal;
import com.pavti.dal.OrderDal;
import com.pavti.dal.PaymentDal;
import com.pavti.dal.TableDal;
import com.pavti.dataobject.customer.CustomerRow;
import com.pavti.dataobject.menu.MenuItem;
import com.pavti.dataobject.order.Order;
import com.pavti.dataobject.order.OrderDetail;
import com.pavti.dataobject.order.OrderFilter;
import com.pavti.dataobject.order.OrderItem;
import com.pavti.dataobject.order.OrderLineRequest;
import com.pavti.dataobject.order.OrderListItem;
import com.pavti.dataobject.order.OrderSaveRequest;
import com.pavti.dataobject.order.TaxLine;
import com.pavti.dataobject.payment.Payment;
import com.pavti.dataobject.platform.Business;
import com.pavti.dataobject.common.ReasonRequest;
import com.pavti.dataobject.table.TableRow;
import com.pavti.filter.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orders and bills — the heart of the app.
 *  - an order stays OPEN while dishes are added; "make bill" gives it the next bill number
 *  - staff always bill the menu price; only the owner can type a price or add a custom line
 *  - discounts need the DISCOUNTS_GIVE tick and are written to the history
 *  - the table follows its order: OPEN → OCCUPIED, BILLED/PART_PAID → BILL_PENDING, else AVAILABLE
 * Money-changing methods lock the order/table row first and use READ COMMITTED, so two
 * phones working on the same table or bill cannot overwrite each other.
 */
@Service
public class OrderBal {

    private static final Set<String> DISCOUNT_TYPES = Set.of("NONE", "PERCENT", "AMOUNT");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final OrderDal orderDal;
    private final PaymentDal paymentDal;
    private final TableDal tableDal;
    private final MenuItemDal menuItemDal;
    private final CustomerDal customerDal;
    private final BusinessDal businessDal;
    private final RightsBal rights;
    private final AuditBal audit;

    public OrderBal(OrderDal orderDal, PaymentDal paymentDal, TableDal tableDal, MenuItemDal menuItemDal, CustomerDal customerDal,
                    BusinessDal businessDal, RightsBal rights, AuditBal audit) {
        this.orderDal = orderDal;
        this.paymentDal = paymentDal;
        this.tableDal = tableDal;
        this.menuItemDal = menuItemDal;
        this.customerDal = customerDal;
        this.businessDal = businessDal;
        this.rights = rights;
        this.audit = audit;
    }

    // ------------------------------------------------------------ read

    public List<OrderListItem> getAll(CurrentUser user, OrderFilter filter) {
        OrderFilter f = filter == null ? new OrderFilter(null, null, false) : filter;
        String search = clean(f.search());
        if (search.length() > 100) search = search.substring(0, 100);
        return orderDal.getAll(user.requireBusinessId(), clean(f.status()), search, Boolean.TRUE.equals(f.mineOnly()) ? user.userId() : null);
    }

    public OrderDetail getDetail(CurrentUser user, long orderId) {
        long bid = user.requireBusinessId();
        Order o = load(bid, orderId);
        return new OrderDetail(o, paymentDal.getByOrder(orderId));
    }

    OrderListItem row(long bid, long orderId) {
        return orderDal.getById(orderId, bid).orElseThrow(() -> ApiException.notFound("Bill not found."));
    }

    public Order load(long bid, long orderId) {
        OrderListItem row = row(bid, orderId);
        List<OrderItem> items = orderDal.getItems(orderId);
        return Order.of(row, items, taxLines(items));
    }

    /** GST grouped by rate, from the saved lines. */
    static List<TaxLine> taxLines(List<OrderItem> items) {
        Map<BigDecimal, BigDecimal[]> byRate = new TreeMap<>();
        for (OrderItem i : items) {
            if (i.taxRate().signum() <= 0 || i.lineAmount().signum() <= 0) continue;
            BigDecimal[] t = byRate.computeIfAbsent(i.taxRate().stripTrailingZeros(), r -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(i.lineAmount().subtract(i.discountAmount()));
            t[1] = t[1].add(i.taxAmount());
        }
        List<TaxLine> out = new ArrayList<>();
        byRate.forEach((rate, t) -> out.add(new TaxLine(rate.setScale(2), t[0], t[1])));
        return out;
    }

    // ------------------------------------------------------------ save

    private record Line(Long itemId, String itemName, String unitName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {}

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order save(CurrentUser user, OrderSaveRequest req) {
        long bid = user.requireBusinessId();
        boolean isNew = req.orderId() == null || req.orderId() == 0;

        // 1. lock what we change first
        OrderListItem existing = null;
        if (!isNew) {
            orderDal.lock(req.orderId(), bid).orElseThrow(() -> ApiException.notFound("Bill not found."));
            existing = row(bid, req.orderId());
            if (!"OPEN".equals(existing.orderStatus())) throw ApiException.badRequest("This bill is already made and cannot be changed.");
        }
        TableRow table = null;
        if (req.tableId() != null && req.tableId() != 0) {
            table = tableDal.lock(req.tableId(), bid).orElseThrow(() -> ApiException.notFound("Table not found."));
            boolean sameOrder = existing != null && Objects.equals(table.currentOrderId(), existing.orderId());
            if (!sameOrder && !"AVAILABLE".equals(table.tableStatus())) {
                throw ApiException.badRequest(table.tableName() + " already has an order. Open it from Tables.");
            }
        }

        // 2. discount
        String discountType = req.discountType() == null ? "NONE" : req.discountType();
        if (!DISCOUNT_TYPES.contains(discountType)) throw ApiException.badRequest("Choose a valid discount.");
        BigDecimal discountValue = "NONE".equals(discountType) || req.discountValue() == null ? BigDecimal.ZERO : req.discountValue().max(BigDecimal.ZERO);
        if ("PERCENT".equals(discountType) && discountValue.compareTo(HUNDRED) > 0) throw ApiException.badRequest("Discount cannot be more than 100%.");
        if (discountValue.signum() > 0 && !rights.hasPermission(user, "DISCOUNTS_GIVE")) {
            throw ApiException.forbidden("You are not allowed to give discounts.");
        }

        // 3. lines — staff always get the menu price (or the price already on this open order)
        if (req.lines() == null || req.lines().isEmpty()) throw ApiException.badRequest("Add at least one dish.");
        if (req.lines().size() > 200) throw ApiException.badRequest("Too many lines on one bill.");
        Map<Long, MenuItem> menu = menuItemDal.getAll(bid).stream().collect(Collectors.toMap(MenuItem::itemId, Function.identity()));
        Map<Long, BigDecimal> keptPrice = new TreeMap<>();
        if (existing != null) {
            for (OrderItem i : orderDal.getItems(existing.orderId())) if (i.itemId() != null) keptPrice.putIfAbsent(i.itemId(), i.unitPrice());
        }
        boolean staff = user.isStaff();
        List<Line> lines = new ArrayList<>();
        for (OrderLineRequest l : req.lines()) {
            BigDecimal qty = l.quantity();
            String label = clean(l.itemName());
            if (qty == null || qty.signum() <= 0) throw ApiException.badRequest("Quantity for \"" + label + "\" must be more than zero.");
            if (qty.compareTo(BigDecimal.valueOf(9999)) > 0 || qty.scale() > 3) throw ApiException.badRequest("Quantity for \"" + label + "\" is not valid.");
            if (l.itemId() != null && l.itemId() != 0) {
                MenuItem it = menu.get(l.itemId());
                if (it == null) throw ApiException.badRequest("A dish on this order is no longer on the menu.");
                BigDecimal price = staff || l.unitPrice() == null
                        ? keptPrice.getOrDefault(it.itemId(), it.price())
                        : TextHelper.money(l.unitPrice());
                if (price.signum() < 0) throw ApiException.badRequest("Price cannot be negative.");
                lines.add(new Line(it.itemId(), it.itemName(), it.unitName(), qty, price, it.taxRate()));
            } else {
                if (staff) throw ApiException.forbidden("Only the owner can add custom lines.");
                if (label.isEmpty()) throw ApiException.badRequest("Every line needs a name.");
                BigDecimal price = TextHelper.money(l.unitPrice());
                BigDecimal tax = l.taxRate() == null ? BigDecimal.ZERO : l.taxRate();
                if (price.signum() < 0) throw ApiException.badRequest("Price cannot be negative.");
                if (tax.signum() < 0 || tax.compareTo(BigDecimal.valueOf(28)) > 0) throw ApiException.badRequest("Tax must be between 0 and 28%.");
                lines.add(new Line(null, label.length() > 150 ? label.substring(0, 150) : label, "pc", qty, price, tax));
            }
        }

        // 4. service charge — staff get the business setting
        Business b = businessDal.getById(bid).orElseThrow(() -> ApiException.notFound("Business not found."));
        BigDecimal serviceChargePct = staff || req.serviceChargePct() == null ? b.serviceChargePct() : req.serviceChargePct();
        if (serviceChargePct.signum() < 0 || serviceChargePct.compareTo(BigDecimal.valueOf(20)) > 0) {
            throw ApiException.badRequest("Service charge must be between 0 and 20%.");
        }

        BillingCalculator.BillResult calc = BillingCalculator.calculate(
                lines.stream().map(l -> new BillingCalculator.LineInput(l.quantity(), l.unitPrice(), l.taxRate())).toList(),
                discountType, discountValue, serviceChargePct);

        // 5. who it is for
        CustomerRow customer = null;
        if (req.customerId() != null && req.customerId() != 0) {
            customer = customerDal.getById(req.customerId(), bid).orElseThrow(() -> ApiException.badRequest("Customer not found."));
        }
        String orderType = table != null ? "DINE_IN" : "COUNTER".equals(req.orderType()) ? "COUNTER" : "TAKEAWAY";
        String customerName = table != null ? table.tableName() : customer != null ? customer.customerName() : "Takeaway";
        String notes = clean(req.notes());
        if (notes.length() > 255) throw ApiException.badRequest("Note is too long.");
        String discountReason = clean(req.discountReason());
        if (discountReason.length() > 255) discountReason = discountReason.substring(0, 255);

        long orderId = orderDal.saveHeader(new OrderDal.Header(isNew ? null : req.orderId(), bid, orderType,
                table == null ? null : table.tableId(), customer == null ? null : customer.customerId(), customerName,
                BusinessDateHelper.today(user.timeZone()), calc.subTotal(), discountType, discountValue, calc.discountAmount(),
                discountReason, calc.taxableAmount(), calc.taxAmount(), serviceChargePct, calc.serviceChargeAmount(),
                calc.totalAmount(), notes), user.userId());

        orderDal.clearItems(orderId, user.userId());
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            BillingCalculator.LineResult r = calc.lines().get(i);
            orderDal.addItem(bid, orderId, l.itemId(), l.itemName(), l.unitName(), l.quantity(), l.unitPrice(), r.lineAmount(),
                    r.discountAmount(), l.taxRate(), r.taxAmount(), r.lineTotal(), user.userId());
        }
        audit.log(user, isNew ? "ORDER_CREATE" : "ORDER_UPDATE", "orders", orderId, customerName, null);
        if (discountValue.signum() > 0) {
            String shown = "PERCENT".equals(discountType) ? discountValue.stripTrailingZeros().toPlainString() + "%" : "Rs " + discountValue;
            audit.log(user, "DISCOUNT_GIVEN", "orders", orderId, shown, discountReason);
        }

        // moved to another table (or to takeaway): free the old one
        if (existing != null && existing.tableId() != null && !existing.tableId().equals(table == null ? null : table.tableId())) {
            freeTable(bid, existing.tableId(), orderId, user.userId());
        }
        if (req.makeBill()) giveBillNo(user, bid, orderId);
        syncTable(row(bid, orderId), user.userId());
        return load(bid, orderId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order makeBill(CurrentUser user, long orderId) {
        long bid = user.requireBusinessId();
        String status = orderDal.lock(orderId, bid).orElseThrow(() -> ApiException.notFound("Bill not found."));
        if (!"OPEN".equals(status)) throw ApiException.badRequest("This bill is already made.");
        giveBillNo(user, bid, orderId);
        syncTable(row(bid, orderId), user.userId());
        return load(bid, orderId);
    }

    private void giveBillNo(CurrentUser user, long bid, long orderId) {
        String billNo = orderDal.makeBill(orderId, bid, user.userId());
        audit.log(user, "BILL_MADE", "orders", orderId, billNo, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(CurrentUser user, ReasonRequest req) {
        long bid = user.requireBusinessId();
        String reason = clean(req.reason());
        if (reason.isEmpty()) throw ApiException.badRequest("Write a reason to cancel.");
        String status = orderDal.lock(req.id(), bid).orElseThrow(() -> ApiException.notFound("Bill not found."));
        if ("CANCELLED".equals(status)) throw ApiException.badRequest("Already cancelled.");
        if (netPaid(paymentDal.getByOrder(req.id())).signum() > 0) throw ApiException.badRequest("Refund the money first, then cancel.");
        OrderListItem o = row(bid, req.id());
        orderDal.cancel(o.orderId(), bid, reason, user.userId());
        if (o.tableId() != null) freeTable(bid, o.tableId(), o.orderId(), user.userId());
        audit.log(user, "BILL_CANCEL", "orders", o.orderId(), o.billNo() != null ? o.billNo() : o.customerName(), reason);
    }

    // ------------------------------------------------------------ money + table (also used by PaymentBal)

    static BigDecimal netPaid(List<Payment> payments) {
        return payments.stream().map(Payment::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** After a payment or refund: new paid/balance/status, then the table. Caller holds the order lock. */
    void refreshMoney(long bid, long orderId, long by) {
        OrderListItem o = row(bid, orderId);
        if ("OPEN".equals(o.orderStatus()) || "CANCELLED".equals(o.orderStatus())) return;
        List<Payment> pays = paymentDal.getByOrder(orderId);
        BigDecimal net = netPaid(pays);
        BigDecimal refunded = pays.stream().filter(p -> "REFUND".equals(p.paymentType())).map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = o.totalAmount().subtract(net).max(BigDecimal.ZERO);
        String status = BillingCalculator.statusAfterPayment(o.orderStatus(), o.totalAmount(), net, refunded);
        orderDal.updateMoney(orderId, net, balance, status, by);
        syncTable(row(bid, orderId), by);
    }

    /** The table follows its order. */
    void syncTable(OrderListItem o, long by) {
        if (o.tableId() == null) return;
        TableRow t = tableDal.getById(o.tableId(), o.businessId()).orElse(null);
        if (t == null) return;
        boolean ours = Objects.equals(t.currentOrderId(), o.orderId());
        switch (o.orderStatus()) {
            case "OPEN" -> tableDal.setStatus(t.tableId(), "OCCUPIED", o.orderId(), by);
            case "BILLED", "PART_PAID" -> {
                // after a refund the table may already have a new order; leave that one alone
                if (ours || t.currentOrderId() == null) tableDal.setStatus(t.tableId(), "BILL_PENDING", o.orderId(), by);
            }
            default -> {
                if (ours) tableDal.setStatus(t.tableId(), "AVAILABLE", null, by);
            }
        }
    }

    private void freeTable(long bid, long tableId, long orderId, long by) {
        tableDal.getById(tableId, bid)
                .filter(t -> Objects.equals(t.currentOrderId(), orderId))
                .ifPresent(t -> tableDal.setStatus(t.tableId(), "AVAILABLE", null, by));
    }
}
