package com.sc2006group5.car_one_stop.config;

import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.enums.auth.UserRole;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("admin@caronestop.com").isPresent()) {
            return; // already seeded
        }

        User admin = User.builder()
                .email("admin@caronestop.com")
                .username("admin")
                .passwordHash(passwordEncoder.encode("admin123"))
                .verified(true)
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        System.out.println("Admin user seeded.");
    }
}
