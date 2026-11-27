package com.procureshield.controller;

import com.procureshield.dto.CartDtos.CartSummaryResponseDto;
import com.procureshield.dto.WishlistDtos.AddWishlistItemRequestDto;
import com.procureshield.dto.WishlistDtos.MoveToCartRequestDto;
import com.procureshield.dto.WishlistDtos.WishlistResponseDto;
import com.procureshield.service.wishlist.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<WishlistResponseDto> getWishlist(Authentication authentication) {
        return ResponseEntity.ok(wishlistService.getWishlist(authentication.getName()));
    }

    @PostMapping
    public ResponseEntity<WishlistResponseDto> addItem(
            Authentication authentication,
            @Valid @RequestBody AddWishlistItemRequestDto request
    ) {
        return ResponseEntity.ok(wishlistService.addItem(authentication.getName(), request.productId()));
    }

    @PostMapping("/toggle/{productId}")
    public ResponseEntity<WishlistResponseDto> toggleItem(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        return ResponseEntity.ok(wishlistService.toggleItem(authentication.getName(), productId));
    }

    @DeleteMapping("/{wishlistItemId}")
    public ResponseEntity<WishlistResponseDto> removeItem(
            Authentication authentication,
            @PathVariable Long wishlistItemId
    ) {
        return ResponseEntity.ok(wishlistService.removeItem(authentication.getName(), wishlistItemId));
    }

    @PostMapping("/{wishlistItemId}/move-to-cart")
    public ResponseEntity<CartSummaryResponseDto> moveToCart(
            Authentication authentication,
            @PathVariable Long wishlistItemId,
            @RequestBody(required = false) MoveToCartRequestDto request
    ) {
        Integer seats = request != null ? request.seats() : 1;
        return ResponseEntity.ok(wishlistService.moveToCart(authentication.getName(), wishlistItemId, seats));
    }
}
