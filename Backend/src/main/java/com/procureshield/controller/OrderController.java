package com.procureshield.controller;

import com.procureshield.dto.OrderDtos.CheckoutRequestDto;
import com.procureshield.dto.OrderDtos.CheckoutResponseDto;
import com.procureshield.dto.OrderDtos.OrderDetailResponseDto;
import com.procureshield.dto.OrderDtos.OrderStatusResponseDto;
import com.procureshield.dto.OrderDtos.OrderStatusUpdateDto;
import com.procureshield.service.order.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponseDto> checkout(
            Authentication authentication,
            @Valid @RequestBody CheckoutRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.checkout(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<OrderDetailResponseDto>> listOrders(Authentication authentication) {
        return ResponseEntity.ok(orderService.listOrders(authentication.getName()));
    }

    @GetMapping("/{poId}")
    public ResponseEntity<OrderDetailResponseDto> getOrderById(
            Authentication authentication,
            @PathVariable Long poId
    ) {
        return ResponseEntity.ok(orderService.getOrderById(authentication.getName(), poId));
    }

    @PatchMapping("/{poId}/status")
    public ResponseEntity<OrderStatusResponseDto> updateStatus(
            Authentication authentication,
            @PathVariable Long poId,
            @Valid @RequestBody OrderStatusUpdateDto request
    ) {
        return ResponseEntity.ok(orderService.updateStatus(authentication.getName(), poId, request));
    }
}