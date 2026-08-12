package com.sc2006group5.car_one_stop.service.profile;

import com.sc2006group5.car_one_stop.dto.profile.ProfileResponse;
import com.sc2006group5.car_one_stop.dto.profile.UpdateProfileRequest;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.wallet.Wallet;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import com.sc2006group5.car_one_stop.repository.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    // TODO: replace with real authenticated user retrieval (JWT)
    private User getUserStub() {
        return userRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Test user not found"));
        //throw new IllegalStateException("TODO: wire current user (JWT).");
    }

    public ProfileResponse viewProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User does not exist"));
        return new ProfileResponse(
                user.getUsername(),
                user.getEmail(),
                user.getCarType(),
                user.getCarModel(),
                user.getChargingType()
        );
    }

    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User does not exist"));

        // only update if value is provided
        if (req.username() != null) user.setUsername(req.username());
        if (req.carModel() != null) user.setCarModel(req.carModel());
        if (req.carType() != null) user.setCarType(req.carType());
        if (req.chargingType() != null) user.setChargingType(req.chargingType());

        User saved = userRepository.save(user);
        return new ProfileResponse(
                saved.getUsername(),
                saved.getEmail(),
                saved.getCarType(),
                saved.getCarModel(),
                saved.getChargingType()
        );
    }
}
