package com.sc2006group5.car_one_stop.repository.wallet;

import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    Optional<Wallet> findByUser(User user);
}
