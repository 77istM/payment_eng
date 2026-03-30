package com.paymenteng.repository;

import com.paymenteng.model.PaymentLifecycleState;
import com.paymenteng.model.RailPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RailPaymentRepository extends JpaRepository<RailPayment, Long> {

    Optional<RailPayment> findByIdempotencyKey(String idempotencyKey);

    List<RailPayment> findByState(PaymentLifecycleState state);
}
