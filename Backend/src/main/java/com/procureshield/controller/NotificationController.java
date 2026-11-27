package com.procureshield.controller;

import com.procureshield.dto.NotificationDtos.NotificationResponseDto;
import com.procureshield.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponseDto>> getNotifications(
            Authentication authentication,
            @RequestParam(name = "unreadOnly", defaultValue = "true") boolean unreadOnly
    ) {
        return ResponseEntity.ok(notificationService.getNotifications(authentication.getName(), unreadOnly));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            Authentication authentication,
            @PathVariable Long id
    ) {
        notificationService.markAsRead(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}