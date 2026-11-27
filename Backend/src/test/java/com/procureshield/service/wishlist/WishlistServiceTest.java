package com.procureshield.service.wishlist;

import com.procureshield.dto.CartDtos.AddCartItemRequestDto;
import com.procureshield.dto.CartDtos.CartSummaryResponseDto;
import com.procureshield.dto.WishlistDtos.WishlistResponseDto;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.Product;
import com.procureshield.entity.RiskLevel;
import com.procureshield.entity.User;
import com.procureshield.entity.WishlistItem;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.ProductRepository;
import com.procureshield.repository.UserRepository;
import com.procureshield.repository.WishlistItemRepository;
import com.procureshield.service.cart.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CartService cartService;

    private WishlistService wishlistService;
    private User user;
    private Product activeProduct;

    @BeforeEach
    void setUp() {
        wishlistService = new WishlistService(
                wishlistItemRepository,
                userRepository,
                productRepository,
                cartService
        );
        user = User.builder()
                .id(1L)
                .email("alex@procureshield.io")
                .departmentId(10L)
                .build();
        activeProduct = Product.builder()
                .id(101L)
                .productName("ShieldCloud SSO")
                .vendorName("ShieldCloud Inc")
                .category("Security")
                .pricePerSeat(new BigDecimal("250.00"))
                .auditStatus(AuditStatus.READY)
                .aiRiskScore(82)
                .aiRiskLevel(RiskLevel.LOW)
                .isActive(true)
                .build();
    }

    @Test
    void addItem_savesNewItemAndAvoidsDuplicates() {
        WishlistItem saved = WishlistItem.builder()
                .id(201L)
                .user(user)
                .product(activeProduct)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(101L)).thenReturn(Optional.of(activeProduct));
        when(wishlistItemRepository.findByUserIdAndProductId(1L, 101L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved));
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenReturn(saved);
        when(wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(saved));

        WishlistResponseDto firstAdd = wishlistService.addItem(user.getEmail(), 101L);
        assertEquals(1, firstAdd.totalCount());
        assertEquals("ShieldCloud SSO", firstAdd.items().get(0).productName());
        verify(wishlistItemRepository).save(any(WishlistItem.class));

        // Second call with an already-saved product should not save a duplicate
        WishlistResponseDto secondAdd = wishlistService.addItem(user.getEmail(), 101L);
        assertEquals(1, secondAdd.totalCount());
        verify(wishlistItemRepository).save(any(WishlistItem.class));
    }

    @Test
    void toggleItem_addsWhenMissingAndRemovesWhenPresent() {
        WishlistItem existing = WishlistItem.builder()
                .id(201L)
                .user(user)
                .product(activeProduct)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(101L)).thenReturn(Optional.of(activeProduct));
        when(wishlistItemRepository.findByUserIdAndProductId(1L, 101L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(existing))
                .thenReturn(List.of());

        WishlistResponseDto afterAdd = wishlistService.toggleItem(user.getEmail(), 101L);
        assertEquals(1, afterAdd.totalCount());

        WishlistResponseDto afterRemove = wishlistService.toggleItem(user.getEmail(), 101L);
        assertEquals(0, afterRemove.totalCount());
        verify(wishlistItemRepository).delete(existing);
    }

    @Test
    void removeItem_deletesOwnedItemAndThrowsWhenMissing() {
        WishlistItem item = WishlistItem.builder().id(201L).user(user).product(activeProduct).build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(wishlistItemRepository.findByIdAndUserId(201L, 1L)).thenReturn(Optional.of(item));
        when(wishlistItemRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        WishlistResponseDto result = wishlistService.removeItem(user.getEmail(), 201L);
        assertEquals(0, result.totalCount());
        verify(wishlistItemRepository).delete(item);

        when(wishlistItemRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> wishlistService.removeItem(user.getEmail(), 999L));
    }

    @Test
    void moveToCart_addsToCartAndRemovesFromWishlist() {
        WishlistItem item = WishlistItem.builder().id(201L).user(user).product(activeProduct).build();
        CartSummaryResponseDto expectedCart = mock(CartSummaryResponseDto.class);

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(wishlistItemRepository.findByIdAndUserId(201L, 1L)).thenReturn(Optional.of(item));
        when(cartService.addOrUpdateItem(user.getEmail(), new AddCartItemRequestDto(101L, 3)))
                .thenReturn(expectedCart);

        CartSummaryResponseDto cart = wishlistService.moveToCart(user.getEmail(), 201L, 3);
        assertEquals(expectedCart, cart);
        verify(wishlistItemRepository).delete(item);
    }

    @Test
    void addItem_rejectsMissingUserOrProductAndInactiveProduct() {
        when(userRepository.findByEmail("ghost@procureshield.io")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> wishlistService.addItem("ghost@procureshield.io", 101L));

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> wishlistService.addItem(user.getEmail(), 404L));

        Product inactive = Product.builder().id(105L).isActive(false).build();
        when(productRepository.findById(105L)).thenReturn(Optional.of(inactive));
        assertThrows(IllegalArgumentException.class, () -> wishlistService.addItem(user.getEmail(), 105L));
        verify(wishlistItemRepository, never()).save(any());
    }
}