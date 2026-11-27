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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    private NotificationService notificationService;

    private User employee;
    private User admin;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                purchaseOrderRepository
        );

        employee = User.builder()
                .id(10L)
                .email("emp@procureshield.io")
                .fullName("Anil Employee")
                .role(UserRole.ROLE_EMPLOYEE)
                .build();

        admin = User.builder()
                .id(30L)
                .email("admin@procureshield.io")
                .fullName("Admin User")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .build();
    }

    @Test
    void getNotifications_respectsUnreadOnlyFlag() {
        Notification unread = Notification.builder()
                .id(701L)
                .recipient(employee)
                .title("Purchase Order APPROVED: PO-2026-0501")
                .message("Purchase order PO-2026-0501 transitioned from PENDING_APPROVAL to APPROVED.")
                .notificationType("PO_STATUS_CHANGE")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(unread));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(unread));

        List<NotificationResponseDto> unreadList = notificationService.getNotifications(employee.getEmail(), true);
        List<NotificationResponseDto> allList = notificationService.getNotifications(employee.getEmail(), false);

        assertEquals(1, unreadList.size());
        assertEquals(701L, unreadList.get(0).notificationId());
        assertFalse(unreadList.get(0).isRead());
        assertEquals(1, allList.size());
    }

    @Test
    void markAsRead_updatesFlagAndRejectsUnownedId() {
        Notification unread = Notification.builder()
                .id(701L)
                .recipient(employee)
                .title("Alert")
                .message("Body")
                .notificationType("PO_STATUS_CHANGE")
                .isRead(false)
                .build();

        when(userRepository.findByEmail(employee.getEmail())).thenReturn(Optional.of(employee));
        when(notificationRepository.findByIdAndRecipientId(701L, 10L)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markAsRead(employee.getEmail(), 701L);

        assertTrue(unread.getIsRead());
        verify(notificationRepository).save(unread);

        when(notificationRepository.findByIdAndRecipientId(999L, 10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> notificationService.markAsRead(employee.getEmail(), 999L));
    }

    @Test
    void recordPoStatusNotification_createsPoStatusChangeAlert() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(702L);
            n.setCreatedAt(LocalDateTime.now());
            return n;
        });

        NotificationResponseDto dto = notificationService.recordPoStatusNotification(
                employee,
                "PO-2026-0501",
                PurchaseOrderStatus.PENDING_APPROVAL,
                PurchaseOrderStatus.APPROVED,
                "Looks good"
        );

        assertEquals(702L, dto.notificationId());
        assertEquals("PO_STATUS_CHANGE", dto.notificationType());
        assertFalse(dto.isRead());
        assertTrue(dto.title().contains("PO-2026-0501"));
    }

    @Test
    void checkContractRenewalWindows_dispatchesAlertsFor60_30_15DaysOnly() {
        LocalDate today = LocalDate.now();
        PurchaseOrder in30Days = PurchaseOrder.builder()
                .id(501L)
                .poNumber("PO-2026-0501")
                .status(PurchaseOrderStatus.ISSUED)
                .contractEndDate(today.plusDays(30))
                .build();
        PurchaseOrder in45Days = PurchaseOrder.builder()
                .id(502L)
                .poNumber("PO-2026-0502")
                .status(PurchaseOrderStatus.ISSUED)
                .contractEndDate(today.plusDays(45))
                .build();
        PurchaseOrder rejectedIn15Days = PurchaseOrder.builder()
                .id(503L)
                .poNumber("PO-2026-0503")
                .status(PurchaseOrderStatus.REJECTED)
                .contractEndDate(today.plusDays(15))
                .build();

        when(userRepository.findByRole(UserRole.ROLE_PROCUREMENT_ADMIN)).thenReturn(List.of(admin));
        when(purchaseOrderRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(in30Days, in45Days, rejectedIn15Days));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.checkContractRenewalWindows();

        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(notificationRepository, never()).save(argThatHasPo("PO-2026-0502"));
    }

    private Notification argThatHasPo(String poNumber) {
        return org.mockito.ArgumentMatchers.argThat(n -> n != null && n.getMessage().contains(poNumber));
    }
}