package com.sc2006group5.car_one_stop.config.auth;

import com.sc2006group5.car_one_stop.enums.auth.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    private final String SECRET =
            "d44ac9ffbf39523fed5c51c3722316ddc05543872c7f2536234b2c5202921b6d37b2f0cc7fb73fec71658fd4d25ddafae38cd18d7a373359e44666f323e80b00\n" +
                    "d204f53ee005c5c44f43d3b403a735cd659561b81115f9db0a9247f8cbbccc1f3448db876848c92fdb73588ac51a0c4ebbcccc9373676a3d63b4210fea76a5af\n" +
                    "e5b869599ab7a0533d29f299121dac3446767b0c337de0d4cbf125007dde057e776ae245ba0ada124891569501cfc17fcf9d187295f32f72c21c589bb3e82204\n" +
                    "4edb740123622fdbcb2b187c63bb313336587dbbb6313fbf379ff272dcc51e1abfb3a2d752edd63c97aa4c156b961022ba7b04d87380f52889e6702f5f9643db\n" +
                    "805f4cb5b7f9f92b40f22fb5308e67dc2f912c651e54cb4dc636bbb9599e608780981a23328d8b58a67dc54cbe272d1ad5ad1cd04cf03dd47d765a6f6a6ec8b2";

    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
    private final long EXPIRATION_MS = 1000L * 60 * 60 * 24 * 7; // 7 days
    private final long TEMPORARY_MS = 1000 * 60 * 15;

    public String generateToken(Long userId, UserRole role) {
        long ExpiryTime;
        if (role.equals(UserRole.USER_UNVERIFIED)) {
            ExpiryTime = TEMPORARY_MS;
        } else {
            ExpiryTime = EXPIRATION_MS;
        }
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role.name())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ExpiryTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long validateTokenAndGetUserID(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Long.parseLong(claims.getSubject());
        } catch (JwtException e) {
            System.out.println(">>> JWT VALIDATION FAILED: " + e.getMessage());
            return null;
        }
    }

    public UserRole getRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String role = claims.get("role", String.class);
            return UserRole.valueOf(role);
        } catch (JwtException e) {
            return null;
        }
    }
}
