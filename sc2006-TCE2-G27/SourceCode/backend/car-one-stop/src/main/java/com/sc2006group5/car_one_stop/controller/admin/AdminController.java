package com.sc2006group5.car_one_stop.controller.admin;

import com.sc2006group5.car_one_stop.dto.admin.AnnouncementRequest;
import com.sc2006group5.car_one_stop.dto.admin.AnnouncementResponse;

import com.sc2006group5.car_one_stop.dto.admin.UserAdminResponse;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.service.admin.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/announcements")
    public Page<AnnouncementResponse> getAnnouncements(Pageable pageable) {
        return adminService.getAnnouncements(pageable);
    }

    @Transactional
    @PostMapping("/announcements")
    public ResponseEntity<Void> createAnnouncement(@RequestBody AnnouncementRequest request){
        adminService.createAnnouncement(request.title(), request.message());
        return ResponseEntity.ok().build();
    }

    @Transactional
    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable Long id){
        adminService.deleteAnnouncement(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public Page<UserAdminResponse> getUsers(@RequestParam(required = false) String search, Pageable pageable) {
        return adminService.getUsers(search, pageable);
    }

    @Transactional
    @PutMapping("/users/{id}/ban")
    public ResponseEntity<Void> banUser(@PathVariable Long id) {
        adminService.banUser(id);
        return ResponseEntity.ok().build();
    }

    @Transactional
    @PutMapping("/users/{id}/unban")
    public ResponseEntity<Void> unbanUser(@PathVariable Long id) {
        adminService.unbanUser(id);
        return ResponseEntity.ok().build();
    }

}
