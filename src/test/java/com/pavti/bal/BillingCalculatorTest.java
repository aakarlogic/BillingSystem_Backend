package com.pavti.bal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pavti.bal.BillingCalculator.BillResult;
import com.pavti.bal.BillingCalculator.LineInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Same cases as the React app's billing.test.ts, so both sides agree on every bill. */
class BillingCalculatorTest {

    private static LineInput line(String qty, String price, String tax) {
        return new LineInput(new BigDecimal(qty), new BigDecimal(price), new BigDecimal(tax));
    }

    private static BigDecimal r(String v) {
        return new BigDecimal(v);
    }

    @Test
    void addsQuantityTimesPrice() {
        BillResult b = BillingCalculator.calculate(List.of(line("2", "10", "0"), line("3", "2.5", "0")), "NONE", null, null);
        assertEquals(r("27.50"), b.subTotal());
        assertEquals(r("27.50"), b.totalAmount());
    }

    @Test
    void ignoresZeroQuantity() {
        BillResult b = BillingCalculator.calculate(List.of(line("0", "10", "0"), line("1", "5", "0")), "NONE", null, null);
        assertEquals(r("5.00"), b.totalAmount());
    }

    @Test
    void taxPerLineGroupedByRate() {
        BillResult b = BillingCalculator.calculate(List.of(line("1", "100", "18"), line("1", "100", "5")), "NONE", null, null);
        assertEquals(r("23.00"), b.taxAmount());
        assertEquals(2, b.taxLines().size());
        assertEquals(r("5.00"), b.taxLines().get(0).taxRate());
        assertEquals(r("223.00"), b.totalAmount());
    }

    @Test
    void percentDiscountBeforeTax() {
        BillResult b = BillingCalculator.calculate(List.of(line("1", "100", "10")), "PERCENT", r("10"), null);
        assertEquals(r("10.00"), b.discountAmount());
        assertEquals(r("9.00"), b.taxAmount());
        assertEquals(r("99.00"), b.totalAmount());
    }

    @Test
    void discountNeverMoreThanBill() {
        BillResult b = BillingCalculator.calculate(List.of(line("1", "5", "0")), "AMOUNT", r("999"), null);
        assertEquals(r("5.00"), b.discountAmount());
        assertEquals(r("0.00"), b.totalAmount());
    }

    @Test
    void discountSharedWithoutLosingAPaisa() {
        BillResult b = BillingCalculator.calculate(List.of(line("1", "3.33", "0"), line("1", "3.33", "0"), line("1", "3.34", "0")),
                "AMOUNT", r("1"), null);
        long shared = b.lines().stream().mapToLong(l -> BillingCalculator.toPaise(l.discountAmount())).sum();
        assertEquals(100, shared);
        assertEquals(r("9.00"), b.totalAmount());
    }

    @Test
    void restaurantDemoFourDosaWithGstAndService() {
        BillResult b = BillingCalculator.calculate(List.of(line("4", "180", "5")), "NONE", null, r("5"));
        assertEquals(r("792.00"), b.totalAmount());
    }

    @Test
    void statusFollowsMoney() {
        assertEquals("BILLED", BillingCalculator.statusAfterPayment("BILLED", r("100"), r("0"), r("0")));
        assertEquals("PART_PAID", BillingCalculator.statusAfterPayment("BILLED", r("100"), r("40"), r("0")));
        assertEquals("PAID", BillingCalculator.statusAfterPayment("BILLED", r("100"), r("100"), r("0")));
        assertEquals("REFUNDED", BillingCalculator.statusAfterPayment("PAID", r("100"), r("0"), r("100")));
        assertEquals("OPEN", BillingCalculator.statusAfterPayment("OPEN", r("100"), r("0"), r("0")));
    }
}
