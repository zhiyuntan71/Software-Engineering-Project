package com.sc2006group5.car_one_stop.controller.profile;

import com.sc2006group5.car_one_stop.dto.profile.ProfileResponse;
import com.sc2006group5.car_one_stop.dto.profile.UpdateProfileRequest;
import com.sc2006group5.car_one_stop.service.profile.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> viewProfile(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(profileService.viewProfile(userId));
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(@AuthenticationPrincipal Long userId, @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(profileService.updateProfile(userId, req));
    }
}