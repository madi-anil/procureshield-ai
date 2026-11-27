package com.procureshield.service.order;

import com.procureshield.dto.OrderDtos.CheckoutRequestDto;
import com.procureshield.dto.OrderDtos.CheckoutResponseDto;
import com.procureshield.dto.OrderDtos.OrderAuditLogDto;
import com.procureshield.dto.OrderDtos.OrderDetailResponseDto;
import com.procureshield.dto.OrderDtos.OrderLineItemDto;
import com.procureshield.dto.OrderDtos.OrderStatusResponseDto;
import com.procureshield.dto.OrderDtos.OrderStatusUpdateDto;
import com.procureshield.dto.OrderDtos.OrderTransitionStatus;
import com.procureshield.entity.AuditStatus;
import com.procureshield.entity.CartItem;
import com.procureshield.entity.Department;
import com.procureshield.entity.PoAuditLog;
import com.procureshield.entity.Product;
import com.procureshield.entity.PurchaseOrder;
import com.procureshield.entity.PurchaseOrderItem;
import com.procureshield.entity.PurchaseOrderStatus;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.CartItemRepository;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.PoAuditLogRepository;
import com.procureshield.repository.PurchaseOrderRepository;
import com.procureshield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PoAuditLogRepository poAuditLogRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${approval.escalation-threshold:10000.00}")
    private BigDecimal escalationThreshold;

    @Transactional
    public CheckoutResponseDto checkout(String userEmail, CheckoutRequestDto request) {
        User requester = getUserByEmail(userEmail);

        if (request.contractEndDate() == null || !request.contractEndDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Contract end date must be a future date.");
        }
        if (request.justification() == null || request.justification().isBlank()) {
            throw new IllegalArgumentException("Justification must not be blank.");
        }
        if (requester.getDepartmentId() == null) {
            throw new IllegalStateException("Requester is not assigned to a department.");
        }

        Department department = departmentRepository.findById(requester.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + requester.getDepartmentId()));

        List<CartItem> cartItems = cartItemRepository.findByUserIdOrderByCreatedAtAsc(requester.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot checkout an empty requisition cart.");
        }

        List<String> unpurchasableProducts = cartItems.stream()
                .map(CartItem::getProduct)
                .filter(p -> Boolean.FALSE.equals(p.getIsActive()) || p.getAuditStatus() != AuditStatus.READY)
                .map(Product::getProductName)
                .toList();

        if (!unpurchasableProducts.isEmpty()) {
            throw new IllegalStateException(
                    "PRODUCT_NOT_AUDITED: Cart contains products that are not active or not READY: "
                            + String.join(", ", unpurchasableProducts)
            );
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        Integer highestRiskScore = null;

        PurchaseOrder order = PurchaseOrder.builder()
                .poNumber(generatePoNumber())
                .requester(requester)
                .department(department)
                .status(PurchaseOrderStatus.PENDING_APPROVAL)
                .justification(request.justification().trim())
                .contractEndDate(request.contractEndDate())
                .build();

        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            BigDecimal unitPrice = product.getPricePerSeat() != null ? product.getPricePerSeat() : BigDecimal.ZERO;
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getSeats()));
            totalAmount = totalAmount.add(subtotal);

            if (product.getAiRiskScore() != null) {
                highestRiskScore = highestRiskScore == null
                        ? product.getAiRiskScore()
                        : Math.max(highestRiskScore, product.getAiRiskScore());
            }

            PurchaseOrderItem lineItem = PurchaseOrderItem.builder()
                    .product(product)
                    .seatQuantity(cartItem.getSeats())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .build();
            order.addItem(lineItem);
        }

        BigDecimal threshold = escalationThreshold != null ? escalationThreshold : new BigDecimal("10000.00");
        boolean requiresEscalation = totalAmount.compareTo(threshold) > 0;

        order.setTotalAmount(totalAmount);
        order.setHighestAiRiskScore(highestRiskScore);
        order.setRequiresEscalation(requiresEscalation);

        PoAuditLog initialLog = PoAuditLog.builder()
                .actor(requester)
                .previousStatus(null)
                .newStatus(PurchaseOrderStatus.PENDING_APPROVAL)
                .comments("Purchase Order created from checkout.")
                .build();
        order.addAuditLog(initialLog);

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        cartItemRepository.deleteAll(cartItems);

        return new CheckoutResponseDto(
                saved.getId(),
                saved.getPoNumber(),
                saved.getStatus(),
                saved.getTotalAmount(),
                saved.getHighestAiRiskScore(),
                saved.getRequiresEscalation(),
                department.getId(),
                requester.getId(),
                saved.getContractEndDate(),
                saved.getCreatedAt()
        );
    }

    public List<OrderDetailResponseDto> listOrders(String userEmail) {
        User actor = getUserByEmail(userEmail);
        List<PurchaseOrder> orders;

        if (actor.getRole() == UserRole.ROLE_PROCUREMENT_ADMIN || actor.getRole() == UserRole.ROLE_AUDITOR) {
            orders = purchaseOrderRepository.findAllByOrderByCreatedAtDesc();
        } else if (actor.getRole() == UserRole.ROLE_DEPARTMENT_HEAD) {
            orders = actor.getDepartmentId() != null
                    ? purchaseOrderRepository.findByDepartmentIdOrderByCreatedAtDesc(actor.getDepartmentId())
                    : List.of();
        } else {
            orders = purchaseOrderRepository.findByRequesterIdOrderByCreatedAtDesc(actor.getId());
        }

        return orders.stream().map(this::toDetailDto).toList();
    }

    public OrderDetailResponseDto getOrderById(String userEmail, Long poId) {
        User actor = getUserByEmail(userEmail);
        PurchaseOrder order = findOrderOrThrow(poId);
        assertCanViewOrder(actor, order);
        return toDetailDto(order);
    }

    @Transactional
    public OrderStatusResponseDto updateStatus(String userEmail, Long poId, OrderStatusUpdateDto request) {
        User actor = getUserByEmail(userEmail);
        PurchaseOrder order = findOrderOrThrow(poId);

        PurchaseOrderStatus previousStatus = order.getStatus();
        OrderTransitionStatus target = request.newStatus();
        boolean isRequester = Objects.equals(order.getRequester().getId(), actor.getId());

        if (target == OrderTransitionStatus.APPROVED) {
            if (previousStatus != PurchaseOrderStatus.PENDING_APPROVAL) {
                throw new IllegalArgumentException("Only PENDING_APPROVAL orders can be approved.");
            }
            if (isRequester) {
                throw new AccessDeniedException("SEPARATION_OF_DUTIES: Requester cannot approve their own purchase order.");
            }
            if (actor.getRole() != UserRole.ROLE_DEPARTMENT_HEAD && actor.getRole() != UserRole.ROLE_PROCUREMENT_ADMIN) {
                throw new AccessDeniedException("Only a Department Head or Procurement Admin can approve purchase orders.");
            }
            if (Boolean.TRUE.equals(order.getRequiresEscalation()) && actor.getRole() != UserRole.ROLE_PROCUREMENT_ADMIN) {
                throw new AccessDeniedException("ESCALATION_REQUIRED: Order exceeds escalation threshold and requires Procurement Admin approval.");
            }
            if (actor.getRole() == UserRole.ROLE_DEPARTMENT_HEAD
                    && !Objects.equals(actor.getDepartmentId(), order.getDepartment().getId())) {
                throw new AccessDeniedException("Department Head can only approve orders for their own department.");
            }

            Department lockedDept = departmentRepository.findByIdWithPessimisticLock(order.getDepartment().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + order.getDepartment().getId()));

            BigDecimal remaining = lockedDept.getRemainingBudget() != null ? lockedDept.getRemainingBudget() : BigDecimal.ZERO;
            if (remaining.compareTo(order.getTotalAmount()) < 0) {
                throw new IllegalArgumentException("Insufficient departmental budget to approve purchase order.");
            }

            BigDecimal reserved = lockedDept.getReservedBudget() != null ? lockedDept.getReservedBudget() : BigDecimal.ZERO;
            lockedDept.setRemainingBudget(remaining.subtract(order.getTotalAmount()));
            lockedDept.setReservedBudget(reserved.add(order.getTotalAmount()));
            departmentRepository.save(lockedDept);

            order.setStatus(PurchaseOrderStatus.APPROVED);
            order.setApprover(actor);

        } else if (target == OrderTransitionStatus.REJECTED) {
            if (previousStatus != PurchaseOrderStatus.PENDING_APPROVAL) {
                throw new IllegalArgumentException("Only PENDING_APPROVAL orders can be rejected or withdrawn.");
            }
            if (request.comments() == null || request.comments().isBlank()) {
                throw new IllegalArgumentException("Comments are required when rejecting or withdrawing a purchase order.");
            }
            if (!isRequester
                    && actor.getRole() != UserRole.ROLE_DEPARTMENT_HEAD
                    && actor.getRole() != UserRole.ROLE_PROCUREMENT_ADMIN) {
                throw new AccessDeniedException("Not authorized to reject this purchase order.");
            }
            if (actor.getRole() == UserRole.ROLE_DEPARTMENT_HEAD
                    && !isRequester
                    && !Objects.equals(actor.getDepartmentId(), order.getDepartment().getId())) {
                throw new AccessDeniedException("Department Head can only reject orders in their own department.");
            }

            order.setStatus(PurchaseOrderStatus.REJECTED);

        } else if (target == OrderTransitionStatus.ISSUED) {
            if (previousStatus != PurchaseOrderStatus.APPROVED) {
                throw new IllegalArgumentException("Only APPROVED orders can be marked as ISSUED.");
            }
            if (isRequester) {
                throw new AccessDeniedException("SEPARATION_OF_DUTIES: Requester cannot issue their own purchase order.");
            }
            if (actor.getRole() != UserRole.ROLE_PROCUREMENT_ADMIN) {
                throw new AccessDeniedException("Only a Procurement Admin can issue a purchase order.");
            }

            order.setStatus(PurchaseOrderStatus.ISSUED);
        }

        PoAuditLog auditLog = PoAuditLog.builder()
                .purchaseOrder(order)
                .actor(actor)
                .previousStatus(previousStatus)
                .newStatus(order.getStatus())
                .comments(request.comments() != null ? request.comments().trim() : null)
                .build();
        PoAuditLog savedLog = poAuditLogRepository.save(auditLog);
        order.getAuditLogs().add(savedLog);

        PurchaseOrder updated = purchaseOrderRepository.save(order);

        return new OrderStatusResponseDto(
                updated.getId(),
                updated.getPoNumber(),
                previousStatus,
                updated.getStatus(),
                actor.getFullName() + " (" + actor.getRole().name() + ")",
                savedLog.getId(),
                updated.getUpdatedAt()
        );
    }

    private void assertCanViewOrder(User actor, PurchaseOrder order) {
        if (actor.getRole() == UserRole.ROLE_PROCUREMENT_ADMIN || actor.getRole() == UserRole.ROLE_AUDITOR) {
            return;
        }
        if (actor.getRole() == UserRole.ROLE_DEPARTMENT_HEAD) {
            if (Objects.equals(actor.getDepartmentId(), order.getDepartment().getId())) {
                return;
            }
            throw new AccessDeniedException("Cannot view purchase orders outside your department.");
        }
        if (!Objects.equals(actor.getId(), order.getRequester().getId())) {
            throw new AccessDeniedException("Cannot view another user's purchase order.");
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private PurchaseOrder findOrderOrThrow(Long poId) {
        return purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found with id: " + poId));
    }

    private String generatePoNumber() {
        long nextSeq = purchaseOrderRepository.count() + 501;
        return String.format("PO-%d-%04d", Year.now().getValue(), nextSeq);
    }

    private OrderDetailResponseDto toDetailDto(PurchaseOrder order) {
        List<OrderLineItemDto> lineItems = order.getItems().stream()
                .map(item -> new OrderLineItemDto(
                        item.getProduct().getId(),
                        item.getProduct().getProductName(),
                        item.getSeatQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()
                ))
                .toList();

        List<OrderAuditLogDto> logs = order.getAuditLogs().stream()
                .map(log -> new OrderAuditLogDto(
                        log.getId(),
                        log.getActor().getFullName(),
                        log.getPreviousStatus(),
                        log.getNewStatus(),
                        log.getComments(),
                        log.getCreatedAt()
                ))
                .toList();

        return new OrderDetailResponseDto(
                order.getId(),
                order.getPoNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getHighestAiRiskScore(),
                order.getRequiresEscalation(),
                order.getRequester().getFullName(),
                order.getDepartment().getName(),
                order.getJustification(),
                order.getContractEndDate(),
                lineItems,
                logs,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}