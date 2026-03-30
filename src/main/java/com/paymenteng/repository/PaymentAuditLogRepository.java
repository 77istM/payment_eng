package com.paymenteng.repository;

import com.paymenteng.model.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {

    List<PaymentAuditLog> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
