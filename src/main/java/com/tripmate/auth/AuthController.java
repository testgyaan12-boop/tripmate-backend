package com.tripmate.auth;

import com.tripmate.auth.dto.AuthDto;
import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ApiResponse<AuthDto.TokenResponse> register(@Valid @RequestBody AuthDto.RegisterRequest req) {
        User u = authService.register(req.getName(), req.getEmail(), req.getPassword(), req.getMobile());
        return issue(u, "Registered");
    }

    @PostMapping("/login")
    public ApiResponse<AuthDto.TokenResponse> login(@Valid @RequestBody AuthDto.LoginRequest req) {
        return issue(authService.login(req.getEmail(), req.getPassword()), "Logged in");
    }

    @PostMapping("/google")
    public ApiResponse<AuthDto.TokenResponse> google(@Valid @RequestBody AuthDto.GoogleRequest req) {
        return issue(authService.loginWithGoogle(req.getIdToken()), "Logged in with Google");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthDto.TokenResponse> refresh(@Valid @RequestBody AuthDto.RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtUtil.parse(req.getRefreshToken());
        } catch (Exception e) {
            throw new BadRequestException("Invalid refresh token");
        }
        if (!"refresh".equals(claims.get("type"))) {
            throw new BadRequestException("Invalid refresh token");
        }
        User u = users.findById(Long.parseLong(claims.getSubject()))
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (u.getRefreshTokenHash() == null
                || !passwordEncoder.matches(req.getRefreshToken(), u.getRefreshTokenHash())) {
            throw new BadRequestException("Refresh token revoked");
        }
        return issue(u, "Refreshed");
    }

    @PostMapping("/logout")
    public ApiResponse<?> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(auth.substring(7));
                users.findById(Long.parseLong(claims.getSubject())).ifPresent(u -> {
                    u.setRefreshTokenHash(null);
                    users.save(u);
                });
            } catch (Exception ignored) {
            }
        }
        return ApiResponse.ok("Logged out", null);
    }

    private ApiResponse<AuthDto.TokenResponse> issue(User u, String msg) {
        String access = jwtUtil.generateAccess(u.getId());
        String refresh = jwtUtil.generateRefresh(u.getId());
        authService.storeRefreshHash(u, refresh);
        return ApiResponse.ok(msg,
                new AuthDto.TokenResponse(access, refresh, u.getId(), u.getName(), u.getEmail()));
    }
}
