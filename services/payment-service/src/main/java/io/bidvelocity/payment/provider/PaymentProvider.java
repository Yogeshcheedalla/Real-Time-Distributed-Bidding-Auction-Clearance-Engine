package io.bidvelocity.payment.provider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Provider abstraction so Razorpay/Stripe drop in without touching the
 * service: implement, register, flip PAYMENT_PROVIDER. The Payment Service
 * never sees raw card data — tokens/refs only (spec §16).
 */
public interface PaymentProvider {

    record ChargeResult(boolean success, String reference, String failureReason) {}
    record RefundResult(boolean success, String reference, String failureReason) {}

    String name();

    /** Create a payment intent / order for the invoice. */
    ChargeResult charge(String idempotencyKey, BigDecimal amount, String currency);

    ChargeResult capture(String reference);

    RefundResult refund(String reference, BigDecimal amount);
}
