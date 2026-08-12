package com.sc2006group5.car_one_stop.service.map;

import com.sc2006group5.car_one_stop.dto.admin.AnnouncementResponse;
import com.sc2006group5.car_one_stop.entity.admin.Announcement;
import com.sc2006group5.car_one_stop.mapper.announcement.AnnouncementMapper;
import com.sc2006group5.car_one_stop.repository.admin.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementRepository announcementRepository;

    public AnnouncementResponse getLatestAnnouncement(){
        Announcement announcement = announcementRepository.findTopByOrderByCreatedAtDesc().orElse(null);
        if (announcement == null) {
            return null;
        }
        return announcementMapper.toResponse(announcement);
    }
}
