// src/main/java/com/sc2006group5/car_one_stop/entity/auth/User.java
package com.sc2006group5.car_one_stop.entity.auth;

import com.sc2006group5.car_one_stop.enums.auth.CarModel;
import com.sc2006group5.car_one_stop.enums.auth.CarType;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;
import com.sc2006group5.car_one_stop.enums.auth.UserRole;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false, unique = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "car_type_model", length = 100)
    private String carTypeModel; //remove??

    @Column(nullable = false)
    private boolean verified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "car_type", length = 20)
    @Enumerated(EnumType.STRING)
    private CarType carType;

    @Column(name = "car_model", length = 50)
    @Enumerated(EnumType.STRING)
    private CarModel carModel;

    @Column(name = "charging_type", length = 30)
    @Enumerated(EnumType.STRING)
    private ChargingType chargingType;

    @Column(nullable = false)
    private boolean banned = false;
}
