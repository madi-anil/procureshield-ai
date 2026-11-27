package com.procureshield.controller;

import com.procureshield.dto.DepartmentBudgetDto;
import com.procureshield.dto.DepartmentBudgetUpdateDto;
import com.procureshield.dto.UserBudgetResponseDto;
import com.procureshield.dto.UserRoleResponseDto;
import com.procureshield.dto.UserRoleUpdateDto;
import com.procureshield.service.user.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserController {

    private final DepartmentService departmentService;

    @GetMapping("/users/me/budget")
    @Operation(summary = "Get current authenticated user's department budget")
    public ResponseEntity<UserBudgetResponseDto> getMyBudget(Authentication authentication) {
        return ResponseEntity.ok(departmentService.getMyBudget(authentication.getName()));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('PROCUREMENT_ADMIN', 'AUDITOR')")
    @Operation(summary = "List all departments and cost-center budget balances")
    public ResponseEntity<List<DepartmentBudgetDto>> getDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @PatchMapping("/departments/{id}/budget")
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Update department annual budget while preserving budget invariant")
    public ResponseEntity<DepartmentBudgetDto> updateDepartmentBudget(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentBudgetUpdateDto request
    ) {
        return ResponseEntity.ok(departmentService.updateAnnualBudget(id, request));
    }

    @PatchMapping("/users/{id}/role")
    @PreAuthorize("hasRole('PROCUREMENT_ADMIN')")
    @Operation(summary = "Update a user's RBAC role and revoke active refresh tokens")
    public ResponseEntity<UserRoleResponseDto> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleUpdateDto request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(departmentService.updateUserRole(id, request, authentication.getName()));
    }
}