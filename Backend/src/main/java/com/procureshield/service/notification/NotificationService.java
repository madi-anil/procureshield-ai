package com.procureshield.service.notification;

import com.procureshield.dto.NotificationDtos.NotificationResponseDto;
import com.procureshield.entity.Notification;
import com.procureshield.entity.PurchaseOrder;
import com.procureshield.entity.PurchaseOrderStatus;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.NotificationRepository;
import com.procureshield.repository.PurchaseOrderRepository;
import com.procureshield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationService {

    private static final Set<Long> RENEWAL_WINDOW_DAYS = Set.of(60L, 30L, 15L);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public List<NotificationResponseDto> getNotifications(String userEmail, boolean unreadOnly) {
        User user = getUserByEmail(userEmail);
        List<Notification> items = unreadOnly
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(user.getId())
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId());

        return items.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void markAsRead(String userEmail, Long notificationId) {
        User user = getUserByEmail(userEmail);
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public NotificationResponseDto recordPoStatusNotification(
            User recipient,
            String poNumber,
            PurchaseOrderStatus previousStatus,
            PurchaseOrderStatus newStatus,
            String comments
    ) {
        String title = "Purchase Order " + newStatus + ": " + poNumber;
        String detail = (comments != null && !comments.isBlank()) ? " Note: " + comments : "";
        String message = "Purchase order " + poNumber + " transitioned from " + previousStatus + " to " + newStatus + "." + detail;

        Notification saved = notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .title(title)
                .message(message)
                .notificationType("PO_STATUS_CHANGE")
                .isRead(false)
                .build());

        return toDto(saved);
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void checkContractRenewalWindows() {
        LocalDate today = LocalDate.now();
        List<User> admins = userRepository.findByRole(UserRole.ROLE_PROCUREMENT_ADMIN);
        if (admins.isEmpty()) {
            return;
        }

        List<PurchaseOrder> activeOrders = purchaseOrderRepository.findAllByOrderByCreatedAtDesc();
        for (PurchaseOrder order : activeOrders) {
            if (order.getContractEndDate() == null || order.getStatus() == PurchaseOrderStatus.REJECTED) {
                continue;
            }
            long daysRemaining = ChronoUnit.DAYS.between(today, order.getContractEndDate());
            if (RENEWAL_WINDOW_DAYS.contains(daysRemaining)) {
                String title = "SLA Renewal Notice (" + daysRemaining + " Days): " + order.getPoNumber();
                String message = "Contract for " + order.getPoNumber() + " ends on " + order.getContractEndDate()
                        + " (" + daysRemaining + " days remaining).";

                for (User admin : admins) {
                    notificationRepository.save(Notification.builder()
                            .recipient(admin)
                            .title(title)
                            .message(message)
                            .notificationType("SLA_RENEWAL_ALERT")
                            .isRead(false)
                            .build());
                }
                log.info("Dispatched {}-day SLA renewal alerts for poNumber={}", daysRemaining, order.getPoNumber());
            }
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }

    private NotificationResponseDto toDto(Notification notification) {
        return new NotificationResponseDto(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}