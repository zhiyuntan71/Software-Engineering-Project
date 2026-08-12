// src/main/java/com/sc2006group5/car_one_stop/entity/booking/Booking.java
package com.sc2006group5.car_one_stop.entity.booking;

import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.enums.booking.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_user", columnList = "user_id"),
        @Index(name = "idx_booking_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @Column(name = "booking_id", length = 64)
    private String bookingId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Keep EV target flexible for now (EVChargingPoint entity can be linked later)
    @Column(name = "ev_charging_point_id", nullable = false, length = 64)
    private String evChargingPointId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(nullable = false)
    @CreationTimestamp
    private Instant createdAt;

    // to remove
    @Column(nullable = false)
    private Instant reservedTime;

    // Null until driver actually checks in at the station
    private Instant checkInTime;

    @Column(nullable = false)
    private int gracePeriodMinutes;

    //to remove
    @Column(nullable = false)
    private Instant startTime;

    //for when booking is completed
    private Instant endTime;

    // Policy-related fields (can be used for fee/refund logic)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal bookingFee;

    @Column(nullable = false)
    private boolean feeCharged;
}
// inUse status mod data after checkedIn cause API wont update availibility