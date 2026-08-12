package com.sc2006group5.car_one_stop.entity.map;

import com.sc2006group5.car_one_stop.entity.auth.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "user_preferences")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // HARD FILTERS
    @Column(precision = 10, scale = 2)
    private BigDecimal maxHourlyRate;      // null = no limit

    @Column
    private Integer minAvailableLots;      // null = no limit

    @Column
    private Boolean requireSheltered;      // null/false = don't require

    // WEIGHTS (simple recommendation tuning)
    @Column(nullable = false)
    private double weightDistance;         // e.g. 0.5

    @Column(nullable = false)
    private double weightPrice;            // e.g. 0.3

    @Column(nullable = false)
    private double weightAvailability;     // e.g. 0.2

    @Column
    private Integer parkingDurationHours;  // null = default 1 hour

    @Column
    private Integer evParkingDurationHours; // null = default 1 hour
}

// subject to change, potentially add for EV
