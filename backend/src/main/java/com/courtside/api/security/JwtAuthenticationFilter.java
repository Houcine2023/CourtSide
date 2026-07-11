package com.courtside.api.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.courtside.api.entities.User;
import com.courtside.api.repositories.UserRepository;
import com.courtside.api.services.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter{
    
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository){
        this.jwtService=jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException{
        String authHeader = request.getHeader("Authorization");

         if(authHeader == null ||
           !authHeader.startsWith("Bearer ")){

            filterChain.doFilter(request,response);
            return;
        }

        String token = authHeader.substring(7);

         try {


            String email =
                    jwtService.extractEmail(token);



            User user =
                    userRepository
                    .findByEmail(email)
                    .orElse(null);



            if(user != null){


                UsernamePasswordAuthenticationToken authentication =

                    new UsernamePasswordAuthenticationToken(

                            user,

                            null,

                            List.of(
                                new SimpleGrantedAuthority(
                                    "ROLE_" +
                                    user.getRole().name()
                                )
                            )
                    );



                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

            }



        }
        catch(Exception e){
            // Invalid/expired token -> continue unauthenticated (Security answers 401 later),
            // but never silently: keep the evidence in the logs.
            logger.debug("JWT rejected: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }



        filterChain.doFilter(request,response);
    }
    
}
