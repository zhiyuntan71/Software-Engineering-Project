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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FacilityRepository facilityRepository;
    @Mock
    private LtaClient ltaClient;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_deductsBookingFeeAndCreatesBookingFeeTransaction() {
        BookingService service = new BookingService(
                bookingRepository,
                walletRepository,
                transactionRepository,
                userRepository,
                facilityRepository,
                ltaClient
        );

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(1L);
        SecurityContextHolder.getContext().setAuthentication(auth);

        User user = User.builder()
                .id(1L)
                .carType(CarType.ELECTRIC)
                .chargingType(ChargingType.CCS)
                .build();

        Wallet wallet = Wallet.builder()
                .walletId("wallet-1")
                .user(user)
                .balance(new BigDecimal("10.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Map<String, Object> evId = new HashMap<>();
        evId.put("evCpId", "CP-1");
        Map<String, Object> plug = new HashMap<>();
        plug.put("plugType", "CCS");
        plug.put("price", "0.5000");
        plug.put("priceType", "kWh");
        plug.put("evIds", List.of(evId));
        Map<String, Object> chargingPoint = new HashMap<>();
        chargingPoint.put("position", "A1");
        chargingPoint.put("plugTypes", List.of(plug));
        EVChargerDto station = EVChargerDto.builder()
                .name("Test Station")
                .chargingPoints(List.of(chargingPoint))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(ltaClient.fetchAllEVChargers()).thenReturn(List.of(station));
        when(bookingRepository.findByUserAndStatus(user, BookingStatus.RESERVED)).thenReturn(List.of());
        when(walletRepository.findByUser(user)).thenReturn(Optional.of(wallet));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = service.create(new CreateBookingRequest("CP-1"));

        assertNotNull(response);
        assertEquals(new BigDecimal("2.00"), response.bookingFee());
        assertEquals(new BigDecimal("8.00"), wallet.getBalance());

        verify(walletRepository).save(wallet);

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction savedTx = txCaptor.getValue();
        assertEquals(TransactionType.BOOKING_FEE, savedTx.getType());
        assertEquals("SUCCESS", savedTx.getStatus());
        assertEquals(new BigDecimal("2.00"), savedTx.getAmount());
    }
}

