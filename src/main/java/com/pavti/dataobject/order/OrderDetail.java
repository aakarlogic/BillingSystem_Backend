package com.pavti.dataobject.order;

import com.pavti.dataobject.payment.Payment;
import java.util.List;

public record OrderDetail(Order order, List<Payment> payments) {}
