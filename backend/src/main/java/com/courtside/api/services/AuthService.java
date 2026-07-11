package com.courtside.api.services;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.courtside.api.dtos.AuthResponse;
import com.courtside.api.dtos.LoginRequest;
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
    public AuthService(PasswordEncoder encoder , UserRepository userRepository, JwtService jwtService) {
        this.encoder=encoder;
        this.userRepository= userRepository;
        this.jwtService = jwtService;
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

        String token = jwtService.generateToken(user);

        return new AuthResponse(token);
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

        String token = jwtService.generateToken(user);


        return new AuthResponse(token);

        

    }
}
