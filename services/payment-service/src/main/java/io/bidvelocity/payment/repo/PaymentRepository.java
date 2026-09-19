package io.bidvelocity.payment.repo;

import io.bidvelocity.payment.domain.Payment;
import io.bidvelocity.payment.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findByAuctionId(Long auctionId);
    List<Payment> findByWinnerIdOrderByCreatedAtDesc(Long winnerId);
    List<Payment> findBySellerIdOrderByCreatedAtDesc(Long sellerId);
    List<Payment> findByStatus(Payment.Status status);
}
