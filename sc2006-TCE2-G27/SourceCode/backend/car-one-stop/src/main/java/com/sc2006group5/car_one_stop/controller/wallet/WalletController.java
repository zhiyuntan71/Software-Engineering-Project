package com.sc2006group5.car_one_stop.controller.wallet;

import com.sc2006group5.car_one_stop.dto.wallet.TopUpRequest;
import com.sc2006group5.car_one_stop.dto.wallet.TransactionResponse;
import com.sc2006group5.car_one_stop.dto.wallet.WalletBalanceResponse;
import com.sc2006group5.car_one_stop.service.wallet.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/balance")
    public ResponseEntity<WalletBalanceResponse> balance() {
        return ResponseEntity.ok(walletService.getBalance());
    }

    @PostMapping("/top-up")
    public ResponseEntity<Void> topUp(@Valid @RequestBody TopUpRequest req) {
        walletService.topUp(req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionResponse>> transactions() {
        return ResponseEntity.ok(walletService.getTransactions());
    }
}