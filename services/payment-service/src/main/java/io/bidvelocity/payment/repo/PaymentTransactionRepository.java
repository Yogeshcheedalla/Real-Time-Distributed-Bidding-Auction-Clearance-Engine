package io.bidvelocity.payment.repo;

import io.bidvelocity.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByPaymentIdOrderByAtAsc(Long paymentId);
}
