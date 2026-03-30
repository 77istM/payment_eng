package com.paymenteng.repository;

import com.paymenteng.model.LedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from LedgerAccount a where a.accountId = :accountId")
	Optional<LedgerAccount> findByAccountIdForUpdate(String accountId);
}
