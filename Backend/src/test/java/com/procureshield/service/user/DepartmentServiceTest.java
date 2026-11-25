package com.procureshield.service.user;

import com.procureshield.dto.DepartmentBudgetDto;
import com.procureshield.dto.DepartmentBudgetUpdateDto;
import com.procureshield.dto.UserBudgetResponseDto;
import com.procureshield.dto.UserRoleResponseDto;
import com.procureshield.dto.UserRoleUpdateDto;
import com.procureshield.entity.Department;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.exception.ResourceNotFoundException;
import com.procureshield.repository.DepartmentRepository;
import com.procureshield.repository.RefreshTokenRepository;
import com.procureshield.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void getMyBudget_returnsDepartmentCostCenterBalances() {
        User employee = User.builder()
                .id(10L)
                .email("emp@procureshield.io")
                .fullName("Anil Employee")
                .role(UserRole.ROLE_EMPLOYEE)
                .departmentId(1L)
                .build();

        Department engineering = Department.builder()
                .id(1L)
                .name("Engineering")
                .costCenterCode("CC-ENG-01")
                .annualBudget(new BigDecimal("100000.00"))
                .remainingBudget(new BigDecimal("65000.00"))
                .reservedBudget(new BigDecimal("15000.00"))
                .spentBudget(new BigDecimal("20000.00"))
                .build();

        when(userRepository.findByEmail("emp@procureshield.io")).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(engineering));

        UserBudgetResponseDto result = departmentService.getMyBudget("EMP@procureshield.io");

        assertEquals(10L, result.userId());
        assertNotNull(result.department());
        assertEquals(new BigDecimal("65000.00"), result.department().remainingBudget());
        assertEquals(new BigDecimal("15000.00"), result.department().reservedBudget());
        assertEquals(new BigDecimal("20000.00"), result.department().spentBudget());
    }

    @Test
    void updateAnnualBudget_preservesInvariantAndLeavesReservedAndSpentUntouched() {
        Department dept = Department.builder()
                .id(1L)
                .name("Engineering")
                .costCenterCode("CC-ENG-01")
                .annualBudget(new BigDecimal("100000.00"))
                .remainingBudget(new BigDecimal("65000.00"))
                .reservedBudget(new BigDecimal("15000.00"))
                .spentBudget(new BigDecimal("20000.00"))
                .build();

        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(dept));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentBudgetDto updated = departmentService.updateAnnualBudget(
                1L, new DepartmentBudgetUpdateDto(new BigDecimal("120000.00"))
        );

        assertEquals(new BigDecimal("120000.00"), updated.annualBudget());
        assertEquals(new BigDecimal("85000.00"), updated.remainingBudget());
        assertEquals(new BigDecimal("15000.00"), updated.reservedBudget());
        assertEquals(new BigDecimal("20000.00"), updated.spentBudget());
    }

    @Test
    void updateAnnualBudget_throwsWhenBelowReservedPlusSpent() {
        Department dept = Department.builder()
                .id(1L)
                .name("Engineering")
                .costCenterCode("CC-ENG-01")
                .annualBudget(new BigDecimal("100000.00"))
                .remainingBudget(new BigDecimal("65000.00"))
                .reservedBudget(new BigDecimal("15000.00"))
                .spentBudget(new BigDecimal("20000.00"))
                .build();

        when(departmentRepository.findByIdWithPessimisticLock(1L)).thenReturn(Optional.of(dept));

        assertThrows(IllegalArgumentException.class, () ->
                departmentService.updateAnnualBudget(1L, new DepartmentBudgetUpdateDto(new BigDecimal("30000.00")))
        );

        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateUserRole_rejectsSelfRoleChange() {
        User admin = User.builder()
                .id(1L)
                .email("admin@procureshield.io")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .build();

        when(userRepository.findByEmail("admin@procureshield.io")).thenReturn(Optional.of(admin));

        assertThrows(AccessDeniedException.class, () ->
                departmentService.updateUserRole(1L, new UserRoleUpdateDto(UserRole.ROLE_AUDITOR), "admin@procureshield.io")
        );

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllActiveTokensByUserId(any());
    }

    @Test
    void updateUserRole_updatesRoleAndRevokesRefreshTokens() {
        User admin = User.builder()
                .id(1L)
                .email("admin@procureshield.io")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .build();

        User target = User.builder()
                .id(2L)
                .email("user@procureshield.io")
                .role(UserRole.ROLE_EMPLOYEE)
                .build();

        when(userRepository.findByEmail("admin@procureshield.io")).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(refreshTokenRepository.revokeAllActiveTokensByUserId(2L)).thenReturn(2);

        UserRoleResponseDto response = departmentService.updateUserRole(
                2L, new UserRoleUpdateDto(UserRole.ROLE_DEPARTMENT_HEAD), "admin@procureshield.io"
        );

        assertEquals(UserRole.ROLE_DEPARTMENT_HEAD, response.role());
        assertTrue(response.refreshTokensRevoked());
        verify(userRepository).save(target);
        verify(refreshTokenRepository).revokeAllActiveTokensByUserId(2L);
    }

    @Test
    void updateUserRole_throwsWhenTargetNotFound() {
        User admin = User.builder()
                .id(1L)
                .email("admin@procureshield.io")
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .build();

        when(userRepository.findByEmail("admin@procureshield.io")).thenReturn(Optional.of(admin));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                departmentService.updateUserRole(99L, new UserRoleUpdateDto(UserRole.ROLE_AUDITOR), "admin@procureshield.io")
        );
    }
}