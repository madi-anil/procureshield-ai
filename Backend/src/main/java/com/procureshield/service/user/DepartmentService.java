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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public UserBudgetResponseDto getMyBudget(String authenticatedEmail) {
        String normalizedEmail = authenticatedEmail.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + normalizedEmail));

        DepartmentBudgetDto departmentDto = null;
        if (user.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(user.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + user.getDepartmentId()));
            departmentDto = toDepartmentDto(dept);
        }

        return new UserBudgetResponseDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                departmentDto
        );
    }

    @Transactional(readOnly = true)
    public List<DepartmentBudgetDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(this::toDepartmentDto)
                .toList();
    }

    @Transactional
    public DepartmentBudgetDto updateAnnualBudget(Long departmentId, DepartmentBudgetUpdateDto request) {
        Department dept = departmentRepository.findByIdWithPessimisticLock(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));

        BigDecimal currentAnnual = dept.getAnnualBudget();
        BigDecimal newAnnual = request.annualBudget();
        BigDecimal delta = newAnnual.subtract(currentAnnual);
        BigDecimal updatedRemaining = dept.getRemainingBudget().add(delta);

        if (updatedRemaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "INSUFFICIENT_BUDGET: New annual budget (" + newAnnual
                            + ") cannot be lower than reserved + spent budget"
            );
        }

        dept.setAnnualBudget(newAnnual);
        dept.setRemainingBudget(updatedRemaining);

        Department saved = departmentRepository.save(dept);
        return toDepartmentDto(saved);
    }

    @Transactional
    public UserRoleResponseDto updateUserRole(Long targetUserId, UserRoleUpdateDto request, String actorEmail) {
        String normalizedActorEmail = actorEmail.trim().toLowerCase(Locale.ROOT);
        User actor = userRepository.findByEmail(normalizedActorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + normalizedActorEmail));

        if (actor.getId().equals(targetUserId)) {
            throw new AccessDeniedException("Admins cannot change their own role");
        }

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found: " + targetUserId));

        UserRole previousRole = targetUser.getRole();
        UserRole newRole = request.role();

        targetUser.setRole(newRole);
        userRepository.save(targetUser);

        int revokedCount = refreshTokenRepository.revokeAllActiveTokensByUserId(targetUserId);

        log.info(
                "User role updated: actorId={}, targetUserId={}, oldRole={}, newRole={}, revokedTokens={}",
                actor.getId(), targetUserId, previousRole, newRole, revokedCount
        );

        return new UserRoleResponseDto(
                targetUser.getId(),
                targetUser.getEmail(),
                targetUser.getRole(),
                revokedCount > 0
        );
    }

    private DepartmentBudgetDto toDepartmentDto(Department dept) {
        return new DepartmentBudgetDto(
                dept.getId(),
                dept.getName(),
                dept.getCostCenterCode(),
                dept.getAnnualBudget(),
                dept.getRemainingBudget(),
                dept.getReservedBudget(),
                dept.getSpentBudget()
        );
    }
}