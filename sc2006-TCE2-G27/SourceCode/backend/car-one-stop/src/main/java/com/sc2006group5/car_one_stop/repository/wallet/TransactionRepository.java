// src/main/java/com/sc2006group5/car_one_stop/repository/wallet/TransactionRepository.java
package com.sc2006group5.car_one_stop.repository.wallet;

import com.sc2006group5.car_one_stop.entity.wallet.Transaction;
import com.sc2006group5.car_one_stop.entity.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByWalletOrderByDateTimeDesc(Wallet wallet);
}