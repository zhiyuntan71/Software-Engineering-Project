package com.sc2006group5.car_one_stop.repository.booking;

import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.booking.Booking;
import com.sc2006group5.car_one_stop.enums.booking.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {
    List<Booking> findByUserOrderByCreatedAtDesc(User user);
    Optional<Booking> findByBookingIdAndUser(String bookingId, User user);
    List<Booking> findByUserAndStatus(User user, BookingStatus status);

    List<Booking> findByStatusInAndEndTimeAfter(List<BookingStatus> statuses, LocalDateTime now);
    List<Booking> findByStatus(BookingStatus status);
    @Query("SELECT b.evChargingPointId FROM Booking b " +
            "WHERE b.status IN ('RESERVED', 'CHECKED_IN')")
    List<String> findOccupiedSlotIds();
}