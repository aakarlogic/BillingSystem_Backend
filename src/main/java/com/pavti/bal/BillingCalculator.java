package com.pavti.bal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bill math, the same rules as the React app's utils/billing.ts.
 * All steps use whole paise (long) so totals never drift by a paisa.
 *  - discount (percent or amount) is taken before tax and shared across lines by value;
 *    the last line takes the rounding remainder
 *  - GST per line on what is left after the discount
 *  - service charge on (subtotal − discount)
 */
public final class BillingCalculator {

    private BillingCalculator() {}

    public record LineInput(BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate) {}

    public record LineResult(BigDecimal lineAmount, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal lineTotal) {}

    public record TaxLineResult(BigDecimal taxRate, BigDecimal taxable, BigDecimal tax) {}

    public record BillResult(List<LineResult> lines, BigDecimal subTotal, BigDecimal discountAmount, BigDecimal taxableAmount,
                             BigDecimal taxAmount, List<TaxLineResult> taxLines, BigDecimal serviceChargeAmount,
                             BigDecimal totalAmount) {}

    public static long toPaise(BigDecimal rupees) {
        return rupees == null ? 0 : rupees.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }

    private static long percentOf(long paise, BigDecimal pct) {
        return BigDecimal.valueOf(paise).multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValue();
    }

    public static BillResult calculate(List<LineInput> inputs, String discountType, BigDecimal discountValue, BigDecimal serviceChargePct) {
        int n = inputs.size();
        long[] amount = new long[n];
        long[] discount = new long[n];
        long[] tax = new long[n];
        long sub = 0;
        for (int i = 0; i < n; i++) {
            LineInput l = inputs.get(i);
            BigDecimal qty = l.quantity() == null ? BigDecimal.ZERO : l.quantity();
            amount[i] = qty.signum() > 0
                    ? qty.multiply(BigDecimal.valueOf(toPaise(l.unitPrice()))).setScale(0, RoundingMode.HALF_UP).longValue()
                    : 0;
            sub += amount[i];
        }

        long disc = 0;
        if ("PERCENT".equals(discountType)) {
            BigDecimal pct = discountValue == null ? BigDecimal.ZERO : discountValue.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100));
            disc = percentOf(sub, pct);
        } else if ("AMOUNT".equals(discountType)) {
            disc = Math.min(sub, Math.max(0, toPaise(discountValue)));
        }

        // share the discount across priced lines; the last one takes the remainder
        int lastPriced = -1;
        for (int i = 0; i < n; i++) if (amount[i] > 0) lastPriced = i;
        long left = disc;
        for (int i = 0; i < n; i++) {
            if (amount[i] == 0) continue;
            long share = i == lastPriced ? left : Math.round((double) disc * amount[i] / sub);
            discount[i] = Math.min(share, amount[i]);
            left -= discount[i];
        }

        Map<BigDecimal, long[]> byRate = new TreeMap<>();
        long totalTax = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal rate = inputs.get(i).taxRate() == null ? BigDecimal.ZERO : inputs.get(i).taxRate().stripTrailingZeros();
            tax[i] = percentOf(amount[i] - discount[i], rate);
            totalTax += tax[i];
            if (rate.signum() > 0 && amount[i] > 0) {
                long[] t = byRate.computeIfAbsent(rate, r -> new long[2]);
                t[0] += amount[i] - discount[i];
                t[1] += tax[i];
            }
        }

        long net = sub - disc;
        long service = percentOf(net, serviceChargePct == null ? BigDecimal.ZERO : serviceChargePct);

        List<LineResult> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lines.add(new LineResult(toRupees(amount[i]), toRupees(discount[i]), toRupees(tax[i]), toRupees(amount[i] - discount[i] + tax[i])));
        }
        List<TaxLineResult> taxLines = new ArrayList<>();
        byRate.forEach((rate, t) -> taxLines.add(new TaxLineResult(rate.setScale(2), toRupees(t[0]), toRupees(t[1]))));

        return new BillResult(lines, toRupees(sub), toRupees(disc), toRupees(net), toRupees(totalTax), taxLines, toRupees(service),
                toRupees(net + totalTax + service));
    }

    /** Order status after money changes (OPEN and CANCELLED never change here). */
    public static String statusAfterPayment(String current, BigDecimal total, BigDecimal netPaid, BigDecimal refunded) {
        if ("OPEN".equals(current) || "CANCELLED".equals(current)) return current;
        long t = toPaise(total);
        long paid = toPaise(netPaid);
        if (paid >= t) return "PAID";
        if (paid > 0) return "PART_PAID";
        if (toPaise(refunded) > 0) return "REFUNDED";
        return "BILLED";
    }
}
