package com.procureshield.controller;

import com.procureshield.dto.PaymentDtos.PaymentReceiptResponseDto;
import com.procureshield.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/settle/{poId}")
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    public ResponseEntity<PaymentReceiptResponseDto> settleOrder(
            Authentication authentication,
            @PathVariable Long poId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.settleOrder(authentication.getName(), poId));
    }
}