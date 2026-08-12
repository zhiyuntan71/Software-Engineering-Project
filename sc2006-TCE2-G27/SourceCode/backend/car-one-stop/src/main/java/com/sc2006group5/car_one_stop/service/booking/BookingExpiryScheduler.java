package com.sc2006group5.car_one_stop.service.booking;

import com.sc2006group5.car_one_stop.entity.booking.Booking;
import com.sc2006group5.car_one_stop.enums.booking.BookingStatus;
import com.sc2006group5.car_one_stop.repository.booking.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireUnclaimedReservations() {
        List<Booking> reserved = bookingRepository
                .findByStatus(BookingStatus.RESERVED);

        Instant now = Instant.now();
        for (Booking b : reserved) {
            Instant deadline = b.getCreatedAt()
                    .plus(b.getGracePeriodMinutes(), ChronoUnit.MINUTES);
            if (now.isAfter(deadline)) {
                b.setStatus(BookingStatus.NO_SHOW);
                b.setEndTime(now);
            }
        }
        bookingRepository.saveAll(reserved);
    }
}