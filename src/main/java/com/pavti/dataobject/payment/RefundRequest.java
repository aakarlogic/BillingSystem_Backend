package com.pavti.dataobject.payment;

import jakarta.validation.constraints.NotNull;

public record RefundRequest(@NotNull(message = "Choose a payment.") Long paymentId, String reason) {}
