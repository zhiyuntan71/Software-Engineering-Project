package com.sc2006group5.car_one_stop.service.wallet;

import com.sc2006group5.car_one_stop.dto.wallet.TopUpRequest;
import com.sc2006group5.car_one_stop.dto.wallet.TransactionResponse;
import com.sc2006group5.car_one_stop.dto.wallet.WalletBalanceResponse;
import com.sc2006group5.car_one_stop.entity.auth.User;
import com.sc2006group5.car_one_stop.entity.wallet.Transaction;
import com.sc2006group5.car_one_stop.entity.wallet.Wallet;
import com.sc2006group5.car_one_stop.enums.wallet.TransactionType;
import com.sc2006group5.car_one_stop.repository.auth.UserRepository;
import com.sc2006group5.car_one_stop.repository.wallet.TransactionRepository;
import com.sc2006group5.car_one_stop.repository.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final Set<Integer> ALLOWED_TOP_UP_AMOUNTS = Set.of(2, 5, 10, 20, 50, 100);

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

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

    public WalletBalanceResponse getBalance() {
        Wallet wallet = getOrCreateWallet(getAuthenticatedUser());
        return new WalletBalanceResponse(wallet.getBalance());
    }

    @Transactional
    public void topUp(TopUpRequest req) {
        validateTopUpAmount(req.amount());
        Objects.requireNonNull(req.paymentMethod(), "paymentMethod is required");

        BigDecimal amount = BigDecimal.valueOf(req.amount());
        Wallet wallet = getOrCreateWallet(getAuthenticatedUser());
        Instant now = Instant.now();

        wallet.credit(amount);
        walletRepository.save(wallet);

        transactionRepository.save(buildTransaction(wallet, amount, TransactionType.TOP_UP, now));
    }

    public List<TransactionResponse> getTransactions() {
        Wallet wallet = getOrCreateWallet(getAuthenticatedUser());
        return transactionRepository.findByWalletOrderByDateTimeDesc(wallet).stream()
                .map(t -> new TransactionResponse(
                        t.getTransactionId(),
                        t.getType(),
                        t.getAmount(),
                        t.getStatus(),
                        t.getDateTime()
                ))
                .toList();
    }

    private Transaction buildTransaction(Wallet wallet, BigDecimal amount, TransactionType type, Instant dateTime) {
        return Transaction.builder()
                .transactionId(UUID.randomUUID().toString())
                .wallet(wallet)
                .amount(amount)
                .dateTime(dateTime)
                .type(type)
                .status("SUCCESS")
                .build();
    }

    private void validateTopUpAmount(Integer amount) {
        if (amount == null || !ALLOWED_TOP_UP_AMOUNTS.contains(amount)) {
            throw new IllegalArgumentException("Invalid top-up amount. Allowed values: " + ALLOWED_TOP_UP_AMOUNTS);
        }
    }
}
