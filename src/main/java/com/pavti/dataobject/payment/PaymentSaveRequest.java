package com.pavti.dataobject.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Customer paid outside the app (cash or UPI scanner); this only records it. */
public record PaymentSaveRequest(
        @NotNull(message = "Choose a bill.") Long orderId,
        @NotBlank(message = "Choose cash or UPI.") String paymentMode,
        @NotNull(message = "Enter the amount.") BigDecimal amount,
        BigDecimal cashReceived,
        @NotBlank(message = "Missing request key.") @Size(max = 64) String requestKey) {}
