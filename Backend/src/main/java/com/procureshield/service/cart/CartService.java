package com.procureshield.service.cart;

import com.procureshield.dto.CartDtos.AddCartItemRequestDto;
import com.procureshield.dto.CartDtos.CartItemResponseDto;
import com.procureshield.dto.CartDtos.CartSummaryResponseDto;
import com.procureshield.entity.CartItem;
import com.procureshield.entity.Department;
import com.procureshield.entity.Product;
import com.procureshield.entity.User;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.CartItemRepository;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.ProductRepository;
import com.procureshield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final int HIGH_RISK_SCORE_THRESHOLD = 60;

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional
    public CartSummaryResponseDto addOrUpdateItem(String userEmail, AddCartItemRequestDto request) {
        User user = getUserByEmail(userEmail);

        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.productId()));

        if (Boolean.FALSE.equals(product.getIsActive())) {
            throw new IllegalArgumentException("Product is inactive and cannot be added to cart: " + product.getId());
        }

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .map(existing -> {
                    existing.setSeats(request.seats());
                    return existing;
                })
                .orElseGet(() -> CartItem.builder()
                        .user(user)
                        .product(product)
                        .seats(request.seats())
                        .build());

        cartItemRepository.save(item);
        return buildCartSummary(user);
    }

    @Transactional(readOnly = true)
    public CartSummaryResponseDto getCart(String userEmail) {
        User user = getUserByEmail(userEmail);
        return buildCartSummary(user);
    }

    @Transactional
    public CartSummaryResponseDto removeItem(String userEmail, Long itemId) {
        User user = getUserByEmail(userEmail);

        CartItem item = cartItemRepository.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found: " + itemId));

        cartItemRepository.delete(item);
        return buildCartSummary(user);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private CartSummaryResponseDto buildCartSummary(User user) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByCreatedAtAsc(user.getId());

        List<CartItemResponseDto> itemDtos = new ArrayList<>();
        int totalSeats = 0;
        BigDecimal estimatedTotal = BigDecimal.ZERO;
        boolean highRiskWarning = false;
        int highestRiskScore = 0;
        List<Long> highRiskProductIds = new ArrayList<>();

        for (CartItem item : items) {
            Product product = item.getProduct();
            BigDecimal pricePerSeat = product.getPricePerSeat() != null ? product.getPricePerSeat() : BigDecimal.ZERO;
            BigDecimal lineTotal = pricePerSeat.multiply(BigDecimal.valueOf(item.getSeats()));

            Integer score = product.getAiRiskScore();
            boolean isHighRisk = score != null && score < HIGH_RISK_SCORE_THRESHOLD;
            if (isHighRisk) {
                highRiskWarning = true;
                highRiskProductIds.add(product.getId());
            }
            if (score != null && score > highestRiskScore) {
                highestRiskScore = score;
            }

            totalSeats += item.getSeats();
            estimatedTotal = estimatedTotal.add(lineTotal);

            itemDtos.add(new CartItemResponseDto(
                    item.getId(),
                    product.getId(),
                    product.getProductName(),
                    product.getVendorName(),
                    product.getCategory(),
                    product.getBillingCycle(),
                    pricePerSeat,
                    item.getSeats(),
                    lineTotal,
                    product.getAuditStatus(),
                    score,
                    product.getAiRiskLevel(),
                    isHighRisk
            ));
        }

        Long departmentId = user.getDepartmentId();
        String departmentName = null;
        BigDecimal remainingBudget = null;
        boolean exceedsBudget = false;

        if (departmentId != null) {
            Department department = departmentRepository.findById(departmentId).orElse(null);
            if (department != null) {
                departmentName = department.getName();
                remainingBudget = department.getRemainingBudget();
                if (remainingBudget != null && estimatedTotal.compareTo(remainingBudget) > 0) {
                    exceedsBudget = true;
                }
            }
        }

        return new CartSummaryResponseDto(
                itemDtos,
                itemDtos.size(),
                totalSeats,
                estimatedTotal,
                departmentId,
                departmentName,
                remainingBudget,
                exceedsBudget,
                highRiskWarning,
                highestRiskScore,
                highRiskProductIds
        );
    }
}