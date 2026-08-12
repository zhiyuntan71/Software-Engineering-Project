package com.sc2006group5.car_one_stop.entity.map;

import com.sc2006group5.car_one_stop.enums.map.FacilityType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(
        name = "facilities",
        indexes = {
                @Index(name = "idx_facility_type", columnList = "type"),
                @Index(name = "idx_facility_lat", columnList = "latitude"),
                @Index(name = "idx_facility_lng", columnList = "longitude")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FacilityType type; // CARPARK / EV

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 240)
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    // ===== extra fields for filtering / recommendation =====

    @Column(precision = 10, scale = 2)
    private BigDecimal hourlyRate; // carpark price (nullable)

    @Column
    private Integer totalLots; // nullable

    @Column
    private Integer availableLots; // nullable

    @Column
    private Boolean sheltered; // nullable

    @Column
    private LocalTime openingTime;

    @Column
    private LocalTime closingTime;
    
    @Column(length = 20)
    private String carparkNo; // HDB carpark number, used to match live availability
}

// ev charger type, potentially ev charging price
