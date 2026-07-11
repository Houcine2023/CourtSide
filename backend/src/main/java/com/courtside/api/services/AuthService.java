package com.courtside.api.services;

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

        String Token = jwtService.generateToken(user);
        
        return new AuthResponse(Token);
    }

    public AuthResponse login(LoginRequest loginRequest){

        User user = userRepository.findByEmail(loginRequest.email())
        .orElseThrow(
            () -> new RuntimeException(
            "Invalid Crediantials"
        ));

        if (!encoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new RuntimeException(
                "invalid crediantials"
            );
        }

        String token = jwtService.generateToken(user);


        return new AuthResponse(token);

        

    }
}
