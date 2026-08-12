package com.sc2006group5.car_one_stop.repository.auth;

import com.sc2006group5.car_one_stop.entity.auth.OTPToken;
import com.sc2006group5.car_one_stop.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OTPTokenRepository extends JpaRepository<OTPToken, Long> {
    Optional<OTPToken> findByUser(User user);
    Optional<OTPToken> findFirstByOtpTokenOrderByExpiresAtDesc(String otpToken);
    void deleteAllByUser(User user);
}
