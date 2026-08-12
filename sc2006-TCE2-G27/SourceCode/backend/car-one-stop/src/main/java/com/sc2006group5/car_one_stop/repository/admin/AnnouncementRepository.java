package com.sc2006group5.car_one_stop.repository.admin;


import com.sc2006group5.car_one_stop.entity.admin.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Optional<Announcement> findTopByOrderByCreatedAtDesc();
}
