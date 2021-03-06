package com.mtemnohud.dbuilder.security.jwt;


import com.mtemnohud.dbuilder.model.response.UserResponse;
import com.mtemnohud.dbuilder.security.model.UserDetails;
import com.mtemnohud.dbuilder.security.web.Messages;
import com.mtemnohud.dbuilder.util.DateUtils;
import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class JwtTokenBuilder {

    public static final String CLAIM_AUTHORITIES = "scopes";
    public static final String USER = "user";

    private final JwtSettings settings;

    public JwtTokenBuilder(JwtSettings settings) {
        this.settings = settings;
    }

    public String encodeToken(UserResponse userResponse) {
        log.trace("[JwtTokenBuilder] [encodeToken] [{}].", userResponse);
        if (StringUtils.isEmpty(userResponse.getUsername()))
            throw new AuthenticationServiceException(Messages.TOKEN_ENCODE_ERROR_NO_USERNAME);

        Claims claims = Jwts.claims().setSubject(userResponse.getUsername());
        claims.put(USER, userResponse);
        claims.put(CLAIM_AUTHORITIES, UserDetails.getAuthorities().stream().map(Object::toString).collect(Collectors.toList()));
        LocalDateTime currentTime = DateUtils.now();
        return Jwts.builder().setClaims(claims).setIssuer(settings.getTokenIssuer()).setIssuedAt(DateUtils.toDate(currentTime))
                .setExpiration(DateUtils.toDate(currentTime.plusMinutes(settings.getTokenExpirationMinutes())))
                .signWith(SignatureAlgorithm.HS512, settings.getTokenSigningKey()).compact();
    }

    public String encodeToken(UserResponse userResponse, User user) {
        log.trace("[JwtTokenBuilder] [encodeToken] [{}].", user);
        if (StringUtils.isEmpty(user.getUsername()))
            throw new AuthenticationServiceException(Messages.TOKEN_ENCODE_ERROR_NO_USERNAME);
        if (user.getAuthorities() == null || user.getAuthorities().isEmpty()) {
            throw new AuthenticationServiceException(Messages.TOKEN_ENCODE_ERROR_NO_PRIVILEGES);
        }
        Claims claims = Jwts.claims().setSubject(user.getUsername());
        claims.put(USER, userResponse);
        claims.put(CLAIM_AUTHORITIES, user.getAuthorities().stream().map(Object::toString).collect(Collectors.toList()));
        LocalDateTime currentTime = DateUtils.now();
        return Jwts.builder().setClaims(claims).setIssuer(settings.getTokenIssuer()).setIssuedAt(DateUtils.toDate(currentTime))
                .setExpiration(DateUtils.toDate(currentTime.plusMinutes(settings.getTokenExpirationMinutes())))
                .signWith(SignatureAlgorithm.HS512, settings.getTokenSigningKey()).compact();
    }

    @SuppressWarnings("unchecked")
    public User decodeToken(String token) {
        log.trace("[JwtTokenBuilder] [decodeToken].");

        try {
            Jws<Claims> claims = Jwts.parser().requireIssuer(settings.getTokenIssuer()).setSigningKey(settings.getTokenSigningKey()).parseClaimsJws(token);
            String username = claims.getBody().getSubject();
            List<String> scopes = claims.getBody().get(CLAIM_AUTHORITIES, List.class);

            List<GrantedAuthority> authorities = scopes.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
            User user = new User(username, "", authorities);
            log.debug("[JwtTokenBuilder] [decodeToken] [{}].", user);
            return user;
        } catch (ExpiredJwtException | IllegalArgumentException e) {
            log.trace("[JwtTokenBuilder] {}.", Messages.JWT_TOKEN_EXPIRED);
            throw new AuthenticationServiceException(Messages.JWT_TOKEN_EXPIRED, e);
        } catch (JwtException e) {
            log.trace("[JwtTokenBuilder] {}.", Messages.JWT_TOKEN_INVALID);
            throw new BadCredentialsException(Messages.JWT_TOKEN_INVALID, e);
        }
    }

}
