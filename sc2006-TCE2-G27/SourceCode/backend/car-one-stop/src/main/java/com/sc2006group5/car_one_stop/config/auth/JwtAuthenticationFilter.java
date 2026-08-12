package com.sc2006group5.car_one_stop.config.auth;

import com.sc2006group5.car_one_stop.enums.auth.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if(authHeader != null && authHeader.startsWith("Bearer ")){
            String token = authHeader.substring(7);
            Long userId = jwtUtil.validateTokenAndGetUserID(token);
            UserRole role =  jwtUtil.getRoleFromToken(token);
            System.out.println(">>> JWT FILTER | userId: " + userId + " | role: " + role);

            if(userId != null && role != null){
                List<GrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(role.name())
                );
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            } else {
                log.warn(
                        "JWT authentication rejected for {} {} (userId={}, role={})",
                        request.getMethod(),
                        request.getRequestURI(),
                        userId,
                        role
                );
            }
        }
        filterChain.doFilter(request, response);
    }
}
