package com.procureshield.config;

import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${procureshield.admin.bootstrap-email:}")
    private String adminEmail;

    @Value("${procureshield.admin.bootstrap-password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByRole(UserRole.ROLE_PROCUREMENT_ADMIN)) {
            return;
        }

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Admin bootstrap skipped: ADMIN_EMAIL or ADMIN_PASSWORD not configured");
            return;
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.info("Admin bootstrap skipped: user already exists for configured admin email");
            return;
        }

        Long defaultDeptId = findPlatformEngineeringDeptId();

        User admin = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName("Procurement Administrator")
                .departmentId(defaultDeptId)
                .role(UserRole.ROLE_PROCUREMENT_ADMIN)
                .isActive(true)
                .build();

        userRepository.save(admin);
        log.info("Bootstrapped initial ROLE_PROCUREMENT_ADMIN userId={}", admin.getId());
    }

    private Long findPlatformEngineeringDeptId() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM departments WHERE cost_center_code = ?",
                (rs, rowNum) -> rs.getLong("id"),
                "CC-ENG-101"
        );
        return ids.isEmpty() ? null : ids.get(0);
    }
}