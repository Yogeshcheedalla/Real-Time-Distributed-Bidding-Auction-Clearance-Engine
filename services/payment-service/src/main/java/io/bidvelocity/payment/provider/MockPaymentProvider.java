package io.bidvelocity.payment.provider;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Development/testing provider: deterministic-ish 82% success using a
 * rotating outcome list so behaviour is reproducible in tests (seeded order),
 * while still exercising retry + failure UI paths.
 */
@Component("mockPaymentProvider")
public class MockPaymentProvider implements PaymentProvider {

    private final AtomicInteger seq = new AtomicInteger();
    private static final List<Boolean> OUTCOMES = List.of(true, true, false, true, true, true); // ~83% success, repeating

    @Override public String name() { return "MOCK"; }

    @Override
    public ChargeResult charge(String idempotencyKey, BigDecimal amount, String currency) {
        boolean ok = OUTCOMES.get(seq.getAndIncrement() % OUTCOMES.size());
        return ok ? new ChargeResult(true, "mock_ch_" + Math.abs(idempotencyKey.hashCode()), null)
                  : new ChargeResult(false, null, "BANK_DECLINED");
    }

    @Override
    public ChargeResult capture(String reference) {
        return new ChargeResult(true, reference, null);
    }

    @Override
    public RefundResult refund(String reference, BigDecimal amount) {
        return new RefundResult(true, "mock_rf_" + Math.abs((reference + amount).hashCode()), null);
    }
}
