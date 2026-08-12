// src/main/java/com/sc2006group5/car_one_stop/controller/booking/BookingController.java
package com.sc2006group5.car_one_stop.controller.booking;

import com.sc2006group5.car_one_stop.dto.booking.BookingResponse;
import com.sc2006group5.car_one_stop.dto.booking.CreateBookingRequest;
import com.sc2006group5.car_one_stop.service.booking.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest req) {
        return ResponseEntity.ok(bookingService.create(req));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String bookingId) {
        bookingService.cancel(bookingId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{bookingId}/no-show")
    public ResponseEntity<Void> noShow(@PathVariable String bookingId, @AuthenticationPrincipal Long userId){
        bookingService.noShow(bookingId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{bookingId}/check-in")
    public ResponseEntity<BookingResponse> checkIn(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.checkIn(bookingId));
    }

    @PostMapping("/{bookingId}/complete")
    public ResponseEntity<BookingResponse> complete(@PathVariable String bookingId) {
        return ResponseEntity.ok(bookingService.complete(bookingId));
    }

    @GetMapping
    public ResponseEntity<List<BookingResponse>> history() {
        return ResponseEntity.ok(bookingService.history());
    }
}
