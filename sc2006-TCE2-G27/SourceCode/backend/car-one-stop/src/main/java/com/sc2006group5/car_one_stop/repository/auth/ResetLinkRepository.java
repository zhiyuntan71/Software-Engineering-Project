// src/main/java/com/sc2006group5/car_one_stop/repository/auth/ResetLinkRepository.java
package com.sc2006group5.car_one_stop.repository.auth;

import com.sc2006group5.car_one_stop.entity.auth.ResetLink;
import com.sc2006group5.car_one_stop.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ResetLinkRepository extends JpaRepository<ResetLink, Long> {
    Optional<ResetLink> findByToken(String token);
    void deleteAllByUser(User user);
    void deleteAllByExpiresAtBefore(Instant cutoff);
}