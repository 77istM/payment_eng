package com.paymenteng.repository;

import com.paymenteng.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PaymentRecord}.
 */
@Repository
public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByTransactionReference(String transactionReference);

    List<PaymentRecord> findByStatus(String status);
}
