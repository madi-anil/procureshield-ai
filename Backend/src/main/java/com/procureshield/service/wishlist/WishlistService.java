package com.procureshield.service.wishlist;

import com.procureshield.dto.CartDtos.AddCartItemRequestDto;
import com.procureshield.dto.CartDtos.CartSummaryResponseDto;
import com.procureshield.dto.WishlistDtos.WishlistItemResponseDto;
import com.procureshield.dto.WishlistDtos.WishlistResponseDto;
import com.procureshield.entity.Product;
import com.procureshield.entity.User;
import com.procureshield.entity.WishlistItem;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.ProductRepository;
import com.procureshield.repository.UserRepository;
import com.procureshield.repository.WishlistItemRepository;
import com.procureshield.service.cart.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    public WishlistResponseDto getWishlist(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<WishlistItemResponseDto> items = wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponseDto)
                .toList();
        return new WishlistResponseDto(items, items.size());
    }

    @Transactional
    public WishlistResponseDto addItem(String userEmail, Long productId) {
        User user = getUserByEmail(userEmail);
        Product product = getActiveProduct(productId);

        Optional<WishlistItem> existing = wishlistItemRepository.findByUserIdAndProductId(user.getId(), product.getId());
        if (existing.isEmpty()) {
            WishlistItem item = WishlistItem.builder()
                    .user(user)
                    .product(product)
                    .build();
            wishlistItemRepository.save(item);
        }

        return getWishlist(userEmail);
    }

    @Transactional
    public WishlistResponseDto toggleItem(String userEmail, Long productId) {
        User user = getUserByEmail(userEmail);
        Product product = getActiveProduct(productId);

        Optional<WishlistItem> existing = wishlistItemRepository.findByUserIdAndProductId(user.getId(), product.getId());
        if (existing.isPresent()) {
            wishlistItemRepository.delete(existing.get());
        } else {
            WishlistItem item = WishlistItem.builder()
                    .user(user)
                    .product(product)
                    .build();
            wishlistItemRepository.save(item);
        }

        return getWishlist(userEmail);
    }

    @Transactional
    public WishlistResponseDto removeItem(String userEmail, Long wishlistItemId) {
        User user = getUserByEmail(userEmail);
        WishlistItem item = wishlistItemRepository.findByIdAndUserId(wishlistItemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found with id: " + wishlistItemId));
        wishlistItemRepository.delete(item);
        return getWishlist(userEmail);
    }

    @Transactional
    public CartSummaryResponseDto moveToCart(String userEmail, Long wishlistItemId, Integer seats) {
        User user = getUserByEmail(userEmail);
        WishlistItem item = wishlistItemRepository.findByIdAndUserId(wishlistItemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist item not found with id: " + wishlistItemId));

        Product product = item.getProduct();
        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Cannot move inactive product to cart.");
        }

        int targetSeats = (seats == null || seats < 1) ? 1 : seats;
        CartSummaryResponseDto cartSummary = cartService.addOrUpdateItem(
                userEmail, new AddCartItemRequestDto(product.getId(), targetSeats)
        );
        wishlistItemRepository.delete(item);
        return cartSummary;
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private Product getActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Product is inactive and cannot be shortlisted.");
        }
        return product;
    }

    private WishlistItemResponseDto toResponseDto(WishlistItem item) {
        Product product = item.getProduct();
        return new WishlistItemResponseDto(
                item.getId(),
                product.getId(),
                product.getProductName(),
                product.getVendorName(),
                product.getCategory(),
                product.getPricePerSeat(),
                product.getAuditStatus(),
                product.getAiRiskScore(),
                product.getAiRiskLevel(),
                item.getCreatedAt()
        );
    }
}