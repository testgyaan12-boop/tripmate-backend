package com.tripmate.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.config.service.ConfigService;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

/**
 * JWT-only auth. Google idToken is verified via Google certs (no Firebase).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ConfigService config;

    public User register(String name, String email, String password, String mobile) {
        if (users.existsByEmail(email)) {
            throw new BadRequestException("Email already registered");
        }
        User u = new User();
        u.setName(name);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setMobile(mobile);
        return users.save(u);
    }

    public User login(String email, String password) {
        User u = users.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid credentials"));
        if (u.getPasswordHash() == null || !passwordEncoder.matches(password, u.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }
        return u;
    }

    public User loginWithGoogle(String idToken) {
        String webClientId = config.get("google.client.id.web", "");
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), new GsonFactory())
                    .setAudience(webClientId.isBlank() ? null : Collections.singleton(webClientId))
                    .build();
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new BadRequestException("Invalid Google token");
            }
            GoogleIdToken.Payload p = token.getPayload();
            String sub = p.getSubject();
            String email = p.getEmail();
            Optional<User> bySub = users.findByGoogleSub(sub);
            if (bySub.isPresent()) return bySub.get();
            Optional<User> byEmail = users.findByEmail(email);
            if (byEmail.isPresent()) {
                User u = byEmail.get();
                u.setGoogleSub(sub);
                return users.save(u);
            }
            User u = new User();
            u.setName((String) p.get("name") != null ? (String) p.get("name") : email);
            u.setEmail(email);
            u.setGoogleSub(sub);
            u.setProfileImage((String) p.get("picture"));
            return users.save(u);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Google verification failed: " + e.getMessage());
        }
    }

    public void storeRefreshHash(User u, String refreshToken) {
        u.setRefreshTokenHash(passwordEncoder.encode(refreshToken));
        users.save(u);
    }
}
