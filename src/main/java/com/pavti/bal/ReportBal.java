package com.pavti.bal;

import com.pavti.common.ApiException;
import com.pavti.common.BusinessDateHelper;
import com.pavti.dal.OrderDal;
import com.pavti.dal.ReportDal;
import com.pavti.dal.TableDal;
import com.pavti.dataobject.common.ChartPoint;
import com.pavti.dataobject.order.OrderListItem;
import com.pavti.dataobject.payment.Payment;
import com.pavti.dataobject.report.DashboardSummary;
import com.pavti.dataobject.report.ItemSalesRow;
import com.pavti.dataobject.report.ReportRequest;
import com.pavti.dataobject.report.SalesReport;
import com.pavti.dataobject.table.TableCard;
import com.pavti.filter.CurrentUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/** Home screen numbers and the sales report. Days are business days (closing at 4 AM). */
@Service
public class ReportBal {

    private final ReportDal reportDal;
    private final OrderDal orderDal;
    private final TableDal tableDal;
    private final RightsBal rights;

    public ReportBal(ReportDal reportDal, OrderDal orderDal, TableDal tableDal, RightsBal rights) {
        this.reportDal = reportDal;
        this.orderDal = orderDal;
        this.tableDal = tableDal;
        this.rights = rights;
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static <T> List<T> where(List<T> rows, Predicate<T> p) {
        return rows.stream().filter(p).toList();
    }

    private static List<ChartPoint> perDay(List<OrderListItem> sales, LocalDate from, LocalDate to) {
        List<ChartPoint> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String key = d.toString();
            days.add(new ChartPoint(key.substring(5), sum(where(sales, o -> key.equals(o.businessDate())), OrderListItem::totalAmount)));
        }
        return days;
    }

    private static List<ChartPoint> top(List<ItemSalesRow> items, Function<ItemSalesRow, BigDecimal> by, int n) {
        return items.stream().sorted(Comparator.comparing(by).reversed()).limit(n).map(i -> new ChartPoint(i.itemName(), by.apply(i))).toList();
    }

    public DashboardSummary dashboard(CurrentUser user) {
        long bid = user.requireBusinessId();
        boolean ownOnly = !rights.hasPermission(user, "SALES_VIEW");
        Long mine = ownOnly ? user.userId() : null;
        LocalDate today = BusinessDateHelper.today(user.timeZone());
        LocalDate from = today.minusDays(13);
        String todayKey = today.toString();

        List<OrderListItem> sales = where(reportDal.orders(bid, from, today), o -> o.sale() && (!ownOnly || o.mine(user.userId())));
        List<OrderListItem> todayBills = where(sales, o -> todayKey.equals(o.businessDate()));
        List<OrderListItem> pending = where(reportDal.unpaid(bid), o -> !ownOnly || o.mine(user.userId()));
        List<Payment> pays = where(reportDal.payments(bid, today, today),
                p -> !ownOnly || (p.receivedBy() != null && p.receivedBy() == user.userId()));
        List<TableCard> tables = tableDal.getFloor(bid);
        List<OrderListItem> recent = orderDal.getAll(bid, null, null, mine);

        return new DashboardSummary(
                ownOnly,
                sum(todayBills, OrderListItem::totalAmount),
                todayBills.size(),
                sum(pays, Payment::signedAmount),
                sum(pending, OrderListItem::balanceAmount),
                pending.size(),
                orderDal.getAll(bid, "OPEN", null, mine).size(),
                tables.stream().filter(t -> !"AVAILABLE".equals(t.tableStatus())).count(),
                tables.size(),
                perDay(sales, from, today),
                top(reportDal.items(bid, from, today, mine), ItemSalesRow::sales, 5),
                recent.subList(0, Math.min(6, recent.size())));
    }

    public SalesReport sales(CurrentUser user, ReportRequest req) {
        long bid = user.requireBusinessId();
        LocalDate from = req.fromDate();
        LocalDate to = req.toDate();
        if (from.isAfter(to)) throw ApiException.badRequest("Start date must be before the end date.");
        if (ChronoUnit.DAYS.between(from, to) > 366) throw ApiException.badRequest("Choose at most one year.");

        List<OrderListItem> all = reportDal.orders(bid, from, to);
        List<OrderListItem> valid = where(all, OrderListItem::sale);
        List<OrderListItem> cancelled = where(all, o -> "CANCELLED".equals(o.orderStatus()));
        List<Payment> pays = reportDal.payments(bid, from, to);
        List<ItemSalesRow> items = reportDal.items(bid, from, to, null);
        List<OrderListItem> unpaid = reportDal.unpaid(bid);

        Map<String, BigDecimal> staff = new LinkedHashMap<>();
        for (OrderListItem o : valid) {
            String n = o.waiterName() == null || o.waiterName().isBlank() ? "-" : o.waiterName();
            staff.merge(n, o.totalAmount(), BigDecimal::add);
        }
        List<ChartPoint> byStaff = staff.entrySet().stream().map(e -> new ChartPoint(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ChartPoint::value).reversed()).toList();

        return new SalesReport(
                valid.size(),
                sum(valid, OrderListItem::totalAmount),
                sum(valid, OrderListItem::subTotal),
                sum(valid, OrderListItem::discountAmount),
                sum(valid, OrderListItem::taxAmount),
                sum(valid, OrderListItem::serviceChargeAmount),
                sum(where(pays, p -> "PAYMENT".equals(p.paymentType())), Payment::amount),
                sum(where(pays, p -> "REFUND".equals(p.paymentType())), Payment::amount),
                sum(where(pays, p -> "CASH".equals(p.paymentMode())), Payment::signedAmount),
                sum(where(pays, p -> "UPI".equals(p.paymentMode())), Payment::signedAmount),
                cancelled.size(),
                sum(cancelled, OrderListItem::totalAmount),
                sum(unpaid, OrderListItem::balanceAmount),
                perDay(valid, from, to),
                top(items, ItemSalesRow::sales, 10),
                top(items, ItemSalesRow::quantity, 10),
                byStaff,
                unpaid,
                cancelled);
    }
}
