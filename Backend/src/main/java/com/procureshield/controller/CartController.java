package com.procureshield.controller;

import com.procureshield.dto.CartDtos.AddCartItemRequestDto;
import com.procureshield.dto.CartDtos.CartSummaryResponseDto;
import com.procureshield.service.cart.CartService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/items")
    @Operation(summary = "Add a product or update seat count in the current user's requisition cart")
    public ResponseEntity<CartSummaryResponseDto> addOrUpdateItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequestDto request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addOrUpdateItem(authentication.getName(), request));
    }

    @GetMapping
    @Operation(summary = "Get current user's requisition cart with aggregated cost, budget check, and AI risk warning")
    public ResponseEntity<CartSummaryResponseDto> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(authentication.getName()));
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Remove a line item from the current user's requisition cart")
    public ResponseEntity<CartSummaryResponseDto> removeItem(
            Authentication authentication,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(cartService.removeItem(authentication.getName(), id));
    }
}