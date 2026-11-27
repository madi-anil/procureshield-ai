package com.procureshield.service.cart;

import com.procureshield.dto.CartDtos.AddCartItemRequestDto;
import com.procureshield.dto.CartDtos.CartSummaryResponseDto;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.CartItem;
import com.procureshield.entity.Department;
import com.procureshield.entity.Product;
import com.procureshield.entity.RiskLevel;
import com.procureshield.entity.User;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.CartItemRepository;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.ProductRepository;
import com.procureshield.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private CartService cartService;
    private User user;
    private Department department;

    @BeforeEach
    void setUp() {
        cartService = new CartService(
                cartItemRepository,
                productRepository,
                userRepository,
                departmentRepository
        );
        user = User.builder()
                .id(1L)
                .email("alex@procureshield.io")
                .departmentId(10L)
                .build();
        department = Department.builder()
                .id(10L)
                .name("Engineering")
                .remainingBudget(new BigDecimal("5000.00"))
                .build();
    }

    @Test
    void addOrUpdateItem_addsNewItemAndAggregatesRiskAndBudget() {
        Product riskyProduct = Product.builder()
                .id(101L)
                .productName("Risky SaaS")
                .vendorName("Vendor X")
                .category("Security")
                .billingCycle("ANNUAL")
                .pricePerSeat(new BigDecimal("500.00"))
                .auditStatus(AuditStatus.READY)
                .aiRiskScore(45)
                .aiRiskLevel(RiskLevel.HIGH)
                .isActive(true)
                .build();

        CartItem savedItem = CartItem.builder()
                .id(501L)
                .user(user)
                .product(riskyProduct)
                .seats(4)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(101L)).thenReturn(Optional.of(riskyProduct));
        when(cartItemRepository.findByUserIdAndProductId(1L, 101L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(savedItem);
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(savedItem));
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(department));

        CartSummaryResponseDto summary = cartService.addOrUpdateItem(
                user.getEmail(),
                new AddCartItemRequestDto(101L, 4)
        );

        assertEquals(1, summary.totalItems());
        assertEquals(4, summary.totalSeats());
        assertEquals(new BigDecimal("2000.00"), summary.estimatedTotalCost());
        assertFalse(summary.exceedsRemainingBudget());
        assertTrue(summary.highRiskWarning());
        assertEquals(List.of(101L), summary.highRiskProductIds());
    }

    @Test
    void addOrUpdateItem_updatesExistingItemSeatsAndFlagsBudgetExceed() {
        Product safeProduct = Product.builder()
                .id(102L)
                .productName("Safe SaaS")
                .vendorName("Vendor Y")
                .category("Productivity")
                .billingCycle("ANNUAL")
                .pricePerSeat(new BigDecimal("1000.00"))
                .auditStatus(AuditStatus.READY)
                .aiRiskScore(85)
                .aiRiskLevel(RiskLevel.LOW)
                .isActive(true)
                .build();

        CartItem existing = CartItem.builder()
                .id(502L)
                .user(user)
                .product(safeProduct)
                .seats(2)
                .build();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(productRepository.findById(102L)).thenReturn(Optional.of(safeProduct));
        when(cartItemRepository.findByUserIdAndProductId(1L, 102L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(CartItem.class))).thenReturn(existing);
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(existing));
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(department));

        CartSummaryResponseDto summary = cartService.addOrUpdateItem(
                user.getEmail(),
                new AddCartItemRequestDto(102L, 6)
        );

        assertEquals(6, existing.getSeats());
        assertEquals(new BigDecimal("6000.00"), summary.estimatedTotalCost());
        assertTrue(summary.exceedsRemainingBudget());
        assertFalse(summary.highRiskWarning());
    }

    @Test
    void getCartAndRemoveItem_workAndThrowWhenMissing() {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartItemRepository.findByUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(department));

        CartSummaryResponseDto emptyCart = cartService.getCart(user.getEmail());
        assertEquals(0, emptyCart.totalItems());

        when(cartItemRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cartService.removeItem(user.getEmail(), 999L));

        CartItem item = CartItem.builder().id(501L).user(user).seats(2).build();
        when(cartItemRepository.findByIdAndUserId(501L, 1L)).thenReturn(Optional.of(item));

        cartService.removeItem(user.getEmail(), 501L);
        verify(cartItemRepository).delete(item);
    }
}