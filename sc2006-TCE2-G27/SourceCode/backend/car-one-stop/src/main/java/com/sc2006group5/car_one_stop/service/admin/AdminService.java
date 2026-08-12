package com.sc2006group5.car_one_stop.service.admin;

import com.sc2006group5.car_one_stop.dto.admin.AnnouncementResponse;
import com.sc2006group5.car_one_stop.dto.admin.UserAdminResponse;
import com.sc2006group5.car_one_stop.entity.admin.Announcement;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.mapper.announcement.AnnouncementMapper;
import com.sc2006group5.car_one_stop.mapper.auth.UserMapper;
import com.sc2006group5.car_one_stop.repository.admin.AnnouncementRepository;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementMapper announcementMapper;

    public Page<UserAdminResponse> getUsers(String search, Pageable pageable) {
        Page<User> users;
        if (search != null && !search.isEmpty()) {
            users = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                    search, search, pageable
            );
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(userMapper::toUserAdminResponse);
    }

    public Page<AnnouncementResponse> getAnnouncements(Pageable pageable){
        Page<Announcement> announcements;
        announcements = announcementRepository.findAll(pageable);
        return announcements.map(announcementMapper::toResponse);
    }

    public void createAnnouncement(String title, String message){
        Announcement announcement = Announcement.builder()
                .Title(title)
                .Message(message)
                .build();
        Announcement saved = announcementRepository.save(announcement);
    }

    public void deleteAnnouncement(Long id){
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        announcementRepository.delete(announcement);
    }

    public void banUser(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User Cant be found "));
        user.setBanned(true);
        userRepository.save(user);
    }

    public void unbanUser(Long id){
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User Cant be found "));
        user.setBanned(false);
        userRepository.save(user);
    }
}
