package io.bidvelocity.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(nullable = false) private String kind;
    @Column(nullable = false) private String detail = "";
    @Column(nullable = false) private Instant at = Instant.now();

    protected PaymentTransaction() {}
    public PaymentTransaction(Long paymentId, String kind, String detail) {
        this.paymentId = paymentId; this.kind = kind; this.detail = detail;
    }
    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public String getKind() { return kind; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
