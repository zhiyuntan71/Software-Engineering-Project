package com.sc2006group5.car_one_stop.service.booking;

import com.sc2006group5.car_one_stop.common.external.lta.LtaClient;
import com.sc2006group5.car_one_stop.dto.booking.BookingResponse;
import com.sc2006group5.car_one_stop.dto.booking.CreateBookingRequest;
import com.sc2006group5.car_one_stop.dto.map.EVChargerDto;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.booking.Booking;
import com.sc2006group5.car_one_stop.entity.wallet.Transaction;
import com.sc2006group5.car_one_stop.entity.wallet.Wallet;
import com.sc2006group5.car_one_stop.enums.auth.CarType;
import com.sc2006group5.car_one_stop.enums.auth.ChargingType;
import com.sc2006group5.car_one_stop.enums.booking.BookingStatus;
import com.sc2006group5.car_one_stop.enums.wallet.TransactionType;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import com.sc2006group5.car_one_stop.repository.booking.BookingRepository;
import com.sc2006group5.car_one_stop.repository.map.FacilityRepository;
import com.sc2006group5.car_one_stop.repository.wallet.TransactionRepository;
import com.sc2006group5.car_one_stop.repository.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final BigDecimal DEFAULT_BOOKING_FEE = new BigDecimal("2.00");
    private static final int DEFAULT_GRACE_PERIOD_MINUTES = 30;

    private final BookingRepository bookingRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final LtaClient ltaClient;

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Number number)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid authentication principal");
        }

        Long userId = number.longValue();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Authenticated user not found"));
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest req) {

        User user = getAuthenticatedUser();
        validateEvBookingEligibility(user);
        validateChargingHeadCompatibility(user, req.evChargingPointId());
        ensureNoActiveReservedBooking(user);

        Wallet wallet = getOrCreateWallet(user);
        if (wallet.getBalance().compareTo(DEFAULT_BOOKING_FEE) < 0) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient wallet balance for booking fee");
        }

        Instant now = Instant.now();

        Booking booking = Booking.builder()
                .bookingId(UUID.randomUUID().toString())
                .user(user)
                .evChargingPointId(req.evChargingPointId())
                .status(BookingStatus.RESERVED)
                .createdAt(now)
                .startTime(now) // to remove startTime
                .reservedTime(now) //to remove reservedtime
                .checkInTime(null)
                .gracePeriodMinutes(DEFAULT_GRACE_PERIOD_MINUTES)
                .startTime(now)
                .endTime(now) // set when user taps "Finish"
                .bookingFee(DEFAULT_BOOKING_FEE)
                .feeCharged(true)
                .build();

        Booking saved = bookingRepository.save(booking);

        wallet.debit(saved.getBookingFee());
        walletRepository.save(wallet);

        transactionRepository.save(Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .wallet(wallet)
                .amount(saved.getBookingFee())
                .dateTime(now)
                .type(TransactionType.BOOKING_FEE)
                .status("SUCCESS")
                .build());

        return toResponse(saved);
    }

    @Transactional
    public BookingResponse checkIn(String bookingId) {
        User user = getAuthenticatedUser();
        Booking booking = bookingRepository.findByBookingIdAndUser(bookingId, user)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cannot check in a cancelled booking");
        }
        if (booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new IllegalStateException("Cannot check in a no-show booking");
        }
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            return toResponse(booking);
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        if (booking.getCheckInTime() == null) {
            booking.setCheckInTime(Instant.now());
        }
        markFacilityInUseMock(booking.getEvChargingPointId());

        Booking saved = bookingRepository.save(booking);
        return toResponse(saved);
    }

    public void cancel(String bookingId) {
        User user = getAuthenticatedUser();
        Booking booking = bookingRepository.findByBookingIdAndUser(bookingId, user)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    public void noShow(String bookingId, Long userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        Booking booking = bookingRepository.findByBookingIdAndUser(bookingId, user)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (booking.getStatus() == BookingStatus.NO_SHOW) {
            return;
        }

        booking.setStatus(BookingStatus.NO_SHOW);
        bookingRepository.save(booking);
    }

    public List<BookingResponse> history() {
        User user = getAuthenticatedUser();
        Map<String, BookingEvSnapshot> snapshotByEvId = buildSnapshotIndex();
        return bookingRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(booking -> toResponse(booking, snapshotByEvId))
                .toList();
    }

    private BookingResponse toResponse(Booking booking) {
        BookingEvSnapshot snapshot = findSnapshotByEvChargingPointId(booking.getEvChargingPointId()).orElse(null);
        return toResponse(booking, snapshot);
    }

    public BookingResponse complete(String bookingId) {
        User user = getAuthenticatedUser();
        Booking booking = bookingRepository.findByBookingIdAndUser(bookingId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Can only complete a checked-in booking");
        }

        booking.setStatus(BookingStatus.COMPLETED);
        booking.setEndTime(Instant.now());
        try {
            return toResponse(bookingRepository.save(booking));
        } catch (DataIntegrityViolationException ex) {
            String rootMessage = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();

            if (rootMessage != null && rootMessage.contains("bookings_status_check")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Booking status constraint mismatch. Please update bookings_status_check to include COMPLETED."
                );
            }
            throw ex;
        }
    }

    private BookingResponse toResponse(Booking booking, Map<String, BookingEvSnapshot> snapshotByEvId) {
        BookingEvSnapshot snapshot = snapshotByEvId.get(normalizeEvChargingPointId(booking.getEvChargingPointId()));
        return toResponse(booking, snapshot);
    }

    private BookingResponse toResponse(Booking booking, BookingEvSnapshot snapshot) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getEvChargingPointId(),
                snapshot != null ? snapshot.locationName() : null,
                snapshot != null ? snapshot.lot() : null,
                snapshot != null ? snapshot.plugType() : null,
                snapshot != null ? snapshot.priceDisplay() : null,
                snapshot != null ? snapshot.priceType() : null,
                snapshot != null ? snapshot.powerRating() : null,
                snapshot != null ? snapshot.currentType() : null,
                snapshot != null ? snapshot.voltage() : null,
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getReservedTime(),
                booking.getCheckInTime(),
                booking.getGracePeriodMinutes(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getBookingFee()
        );
    }

    private Wallet getOrCreateWallet(User user) {
        return walletRepository.findByUser(user).orElseGet(() -> {
            Instant now = Instant.now();
            Wallet wallet = Wallet.builder()
                    .walletId(UUID.randomUUID().toString())
                    .user(user)
                    .balance(BigDecimal.ZERO)
                    .updatedAt(now)
                    .build();
            return walletRepository.save(wallet);
        });
    }

    private void markFacilityInUseMock(String evChargingPointId) {
        Long facilityId;
        try {
            facilityId = Long.parseLong(evChargingPointId);
        } catch (NumberFormatException ignored) {
            return;
        }

        facilityRepository.findById(facilityId).ifPresent(facility -> {
            Integer availableLots = facility.getAvailableLots();
            if (availableLots != null && availableLots > 0) {
                facility.setAvailableLots(availableLots - 1);
                facilityRepository.save(facility);
            }
        });
    }

    private void validateEvBookingEligibility(User user) {
        if (user.getCarType() != CarType.ELECTRIC) {
            throw new ResponseStatusException(FORBIDDEN, "Only electric vehicles can book EV chargers");
        }
        if (user.getChargingType() == null || user.getChargingType() == ChargingType.NOT_APPLICABLE) {
            throw new ResponseStatusException(BAD_REQUEST, "Saved charging type is required for EV booking");
        }
    }

    private void ensureNoActiveReservedBooking(User user) {
        List<Booking> activeReserved = bookingRepository.findByUserAndStatus(user, BookingStatus.RESERVED);
        if (!activeReserved.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "You already have an active booking");
        }
    }

    private void validateChargingHeadCompatibility(User user, String evChargingPointId) {
        String requestedId = evChargingPointId == null ? "" : evChargingPointId.trim();
        if (requestedId.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid charging point ID");
        }

        BookingEvSnapshot selected = findSnapshotByEvChargingPointId(requestedId)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Selected charging point was not found"));

        if (!matchesSavedChargingType(selected.plugType(), user.getChargingType())) {
            throw new ResponseStatusException(BAD_REQUEST, "Selected charging head is incompatible with your saved charging type");
        }
    }

    private Optional<BookingEvSnapshot> findSnapshotByEvChargingPointId(String evChargingPointId) {
        List<EVChargerDto> stations = ltaClient.fetchAllEVChargers();
        for (EVChargerDto station : stations) {
            List<Object> chargingPoints = station.getChargingPoints();
            if (chargingPoints == null) continue;

            for (Object cpObj : chargingPoints) {
                if (!(cpObj instanceof Map<?, ?> cpMap)) continue;
                Object plugTypesObj = cpMap.get("plugTypes");
                if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
                String lot = asString(cpMap.get("position"));

                for (Object plugObj : plugTypes) {
                    if (!(plugObj instanceof Map<?, ?> plugMap)) continue;
                    if (containsEvChargingPointId(plugMap, evChargingPointId)) {
                        String plugType = asString(plugMap.get("plugType"));
                        String priceType = asString(plugMap.get("priceType"));
                        String powerRating = asString(plugMap.get("powerRating"));
                        String currentType = asString(plugMap.get("currentType"));
                        String voltage = asString(plugMap.get("voltage"));
                        String priceDisplay = buildPriceDisplay(plugMap);
                        if (currentType == null || currentType.isBlank()) {
                            currentType = deriveCurrentType(powerRating);
                        }
                        return Optional.of(new BookingEvSnapshot(
                                station.getName(),
                                lot,
                                plugType,
                                priceDisplay,
                                priceType,
                                powerRating,
                                currentType,
                                voltage
                        ));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean containsEvChargingPointId(Map<?, ?> plugMap, String evChargingPointId) {
        String normalizedRequested = normalizeEvChargingPointId(evChargingPointId);
        Object evIdsObj = plugMap.get("evIds");
        if (!(evIdsObj instanceof List<?> evIds)) return false;

        for (Object evIdObj : evIds) {
            if (!(evIdObj instanceof Map<?, ?> evIdMap)) continue;
            Object rawId = evIdMap.get("evCpId");
            if (rawId != null && normalizeEvChargingPointId(rawId.toString()).equals(normalizedRequested)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, BookingEvSnapshot> buildSnapshotIndex() {
        List<EVChargerDto> stations = ltaClient.fetchAllEVChargers();
        Map<String, BookingEvSnapshot> index = new java.util.HashMap<>();

        for (EVChargerDto station : stations) {
            List<Object> chargingPoints = station.getChargingPoints();
            if (chargingPoints == null) continue;

            for (Object cpObj : chargingPoints) {
                if (!(cpObj instanceof Map<?, ?> cpMap)) continue;
                Object plugTypesObj = cpMap.get("plugTypes");
                if (!(plugTypesObj instanceof List<?> plugTypes)) continue;
                String lot = asString(cpMap.get("position"));

                for (Object plugObj : plugTypes) {
                    if (!(plugObj instanceof Map<?, ?> plugMap)) continue;
                    String plugType = asString(plugMap.get("plugType"));
                    String priceType = asString(plugMap.get("priceType"));
                    String powerRating = asString(plugMap.get("powerRating"));
                    String currentType = asString(plugMap.get("currentType"));
                    String voltage = asString(plugMap.get("voltage"));
                    String priceDisplay = buildPriceDisplay(plugMap);
                    if (currentType == null || currentType.isBlank()) {
                        currentType = deriveCurrentType(powerRating);
                    }

                    BookingEvSnapshot snapshot = new BookingEvSnapshot(
                            station.getName(),
                            lot,
                            plugType,
                            priceDisplay,
                            priceType,
                            powerRating,
                            currentType,
                            voltage
                    );

                    Object evIdsObj = plugMap.get("evIds");
                    if (!(evIdsObj instanceof List<?> evIds)) continue;
                    for (Object evIdObj : evIds) {
                        if (!(evIdObj instanceof Map<?, ?> evIdMap)) continue;
                        Object rawId = evIdMap.get("evCpId");
                        if (rawId == null) continue;
                        index.putIfAbsent(normalizeEvChargingPointId(rawId.toString()), snapshot);
                    }
                }
            }
        }
        return index;
    }

    private boolean matchesSavedChargingType(String plugType, ChargingType savedType) {
        if (savedType == null || savedType == ChargingType.NOT_APPLICABLE) {
            return false;
        }

        String plug = normalize(plugType);
        return switch (savedType) {
            case TYPE_1_J1772 -> plug.contains("type1") || plug.contains("j1772");
            case TYPE_2_MENNEKES -> plug.contains("type2") || plug.contains("mennekes");
            case CCS -> plug.contains("ccs");
            case CHADEMO -> plug.contains("chademo");
            case TESLA_SUPERCHARGER -> plug.contains("tesla") || plug.contains("supercharger");
            default -> false;
        };
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String normalizeEvChargingPointId(String value) {
        if (value == null) return "";
        return value.trim().toUpperCase();
    }

    private String buildPriceDisplay(Map<?, ?> plugMap) {
        Object priceObj = plugMap.get("price");
        if (priceObj == null) return null;
        Object typeObj = plugMap.get("priceType");
        String type = typeObj == null ? "kWh" : typeObj.toString();
        try {
            double p = Double.parseDouble(priceObj.toString());
            return String.format("$%.4f/%s", p, type);
        } catch (NumberFormatException ignored) {
            return "$" + priceObj + "/" + type;
        }
    }

    private String deriveCurrentType(String powerRating) {
        if (powerRating == null) return null;
        try {
            return Double.parseDouble(powerRating) < 22 ? "AC" : "DC";
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record BookingEvSnapshot(
            String locationName,
            String lot,
            String plugType,
            String priceDisplay,
            String priceType,
            String powerRating,
            String currentType,
            String voltage
    ) {}
}
