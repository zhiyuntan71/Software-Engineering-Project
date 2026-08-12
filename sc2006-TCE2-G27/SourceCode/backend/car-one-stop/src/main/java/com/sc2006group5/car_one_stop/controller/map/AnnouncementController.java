package com.sc2006group5.car_one_stop.controller.map;

import com.sc2006group5.car_one_stop.dto.admin.AnnouncementResponse;
import com.sc2006group5.car_one_stop.entity.admin.Announcement;
import com.sc2006group5.car_one_stop.service.map.AnnouncementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcement/latest")
public class AnnouncementController {

    @Autowired
    private AnnouncementService announcementService;

    @GetMapping
    public AnnouncementResponse getLatestAnnouncement() {
        return announcementService.getLatestAnnouncement();
    }
}
