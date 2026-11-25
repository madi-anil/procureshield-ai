package com.procureshield.service.auth;

import com.procureshield.dto.AuthTokenResponseDto;
import com.procureshield.dto.LoginRequestDto;
import com.procureshield.dto.RefreshTokenRequestDto;
import com.procureshield.dto.RegisterRequestDto;
import com.procureshield.dto.RegisterResponseDto;
import com.procureshield.dto.TokenRefreshResponseDto;
import com.procureshield.entity.RefreshToken;
import com.procureshield.entity.User;
import com.procureshield.entity.UserRole;
import com.procureshield.exception.DuplicateResourceException;
import com.procureshield.exception.InvalidTokenException;
import com.procureshield.repository.RefreshTokenRepository;
import com.procureshield.repository.UserRepository;
import com.procureshield.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 604800000L);
    }

    @Test
    void shouldRegisterUserWithEmployeeRoleWhenRequestIsValid() {
        RegisterRequestDto request = new RegisterRequestDto(
                "Alice.User@Company.com",
                "Secret@123",
                "Alice User",
                1L
        );

        when(userRepository.existsByEmail("alice.user@company.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret@123")).thenReturn("$2a$12$hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            u.setCreatedAt(LocalDateTime.now());
            return u;
        });

        RegisterResponseDto response = authService.register(request);

        assertEquals(10L, response.userId());
        assertEquals("alice.user@company.com", response.email());
        assertEquals(UserRole.ROLE_EMPLOYEE, response.role());
        assertTrue(response.isActive());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowDuplicateExceptionWhenEmailAlreadyExists() {
        RegisterRequestDto request = new RegisterRequestDto(
                "Alice.User@Company.com",
                "Secret@123",
                "Alice User",
                1L
        );

        when(userRepository.existsByEmail("alice.user@company.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLoginSuccessfullyWhenCredentialsAreValid() {
        User user = User.builder()
                .id(10L)
                .email("alice@company.com")
                .passwordHash("$2a$12$hashedPassword")
                .fullName("Alice User")
                .departmentId(1L)
                .role(UserRole.ROLE_EMPLOYEE)
                .isActive(true)
                .build();

        when(userRepository.findByEmail("alice@company.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("jwt-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthTokenResponseDto response = authService.login(
                new LoginRequestDto("ALICE@COMPANY.COM", "Secret@123")
        );

        assertEquals("Bearer", response.tokenType());
        assertEquals("jwt-access-token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertEquals(900L, response.expiresInSeconds());
        assertEquals("Alice User", response.user().fullName());
    }

    @Test
    void shouldRotateRefreshTokenWhenValidAndNotExpired() {
        RefreshToken existingToken = RefreshToken.builder()
                .id(55L)
                .userId(10L)
                .tokenHash("some-hash")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .revoked(false)
                .build();

        User user = User.builder()
                .id(10L)
                .email("alice@company.com")
                .role(UserRole.ROLE_EMPLOYEE)
                .isActive(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(existingToken));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(user)).thenReturn("new-jwt-access-token");
        when(jwtTokenProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenRefreshResponseDto response = authService.refresh(
                new RefreshTokenRequestDto("old-raw-refresh-token")
        );

        assertTrue(existingToken.getRevoked());
        assertEquals("new-jwt-access-token", response.accessToken());
        assertNotNull(response.refreshToken());
        assertNotEquals("old-raw-refresh-token", response.refreshToken());
    }

    @Test
    void shouldThrowInvalidTokenExceptionWhenRefreshTokenIsRevoked() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(55L)
                .userId(10L)
                .tokenHash("revoked-hash")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(revokedToken));

        assertThrows(
                InvalidTokenException.class,
                () -> authService.refresh(new RefreshTokenRequestDto("revoked-token"))
        );
    }
}