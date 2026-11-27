package com.procureshield.dto;

import java.time.LocalDateTime;

public class NotificationDtos {

    public record NotificationResponseDto(
            Long notificationId,
            String title,
            String message,
            String notificationType,
            Boolean isRead,
            LocalDateTime createdAt
    ) {
    }
}