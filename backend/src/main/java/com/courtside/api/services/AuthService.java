package com.courtside.api.services;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import com.courtside.api.dtos.AuthResponse;
import com.courtside.api.dtos.LoginRequest;
import com.courtside.api.dtos.RefreshRequest;
import com.courtside.api.dtos.RegisterRequest;
import com.courtside.api.entities.Role;
import com.courtside.api.entities.User;
import com.courtside.api.exceptions.EmailAlreadyExistsException;
import com.courtside.api.repositories.UserRepository;

@Service
public class AuthService {

    private final PasswordEncoder encoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(PasswordEncoder encoder , UserRepository userRepository, JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.encoder=encoder;
        this.userRepository= userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }


    public AuthResponse register(RegisterRequest request) {

        if(userRepository.existsByEmail(request.email())){

            throw new EmailAlreadyExistsException("email already exists");
        }

        User user = new User();

        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(Role.MEMBER);

        userRepository.save(user);

        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest loginRequest){

        // Same generic message whether the email or the password is wrong:
        // revealing which one failed lets attackers enumerate accounts.
        User user = userRepository.findByEmail(loginRequest.email())
        .orElseThrow(
            () -> new BadCredentialsException("Invalid credentials")
        );

        if (!encoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return issueTokens(user);
    }

    /**
     * Exchanges a valid refresh token for a BRAND NEW pair (token rotation).
     *
     * Why rotate instead of reusing the same refresh token? Because a token used only
     * once is detectable when it shows up a second time — that is the trap that catches
     * a thief (see RefreshTokenService.consume).
     *
     * @Transactional wraps consume() + issue() in ONE unit of work: either the old token
     * is revoked AND the new one is created, or neither happens. Without it, a crash in
     * between could revoke the user's session while handing back nothing.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refresh(RefreshRequest request) {
        User user = refreshTokenService.consume(request.refreshToken());
        return issueTokens(user);
    }

    /**
     * Logout = revoke the refresh token.
     *
     * Note the deliberate limitation: the access token stays valid until it expires
     * (<= 15 min), because a JWT cannot be un-issued. This is the accepted price of a
     * stateless API; shorten the access-token lifetime if the risk is unacceptable, or
     * add a denylist — which reintroduces the server-side state we were avoiding.
     */
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /** One place that mints a pair — register, login and refresh all behave identically. */
    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken);
    }
}
