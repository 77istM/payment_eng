package com.paymenteng.repository;

import com.paymenteng.model.PaymentLifecycleState;
import com.paymenteng.model.RailPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RailPaymentRepository extends JpaRepository<RailPayment, Long> {

    Optional<RailPayment> findByIdempotencyKey(String idempotencyKey);

    List<RailPayment> findByState(PaymentLifecycleState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RailPayment p where p.id = :id")
    Optional<RailPayment> findByIdForUpdate(Long id);
}
