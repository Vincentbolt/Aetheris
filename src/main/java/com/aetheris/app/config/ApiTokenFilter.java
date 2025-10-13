package com.aetheris.app.config;

import com.aetheris.app.model.User;
import com.aetheris.app.repo.UserRepository;
import com.aetheris.app.security.JwtUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip common static resources
        if (path.matches(".+\\.(ico|png|jpg|jpeg|gif|svg)$")) {
            return true;
        }

        // Skip other public endpoints
        return path.startsWith("/api/auth")
            || path.startsWith("/api/test/public")
            || path.startsWith("/api/market")
            || path.startsWith("/h2-console")
            || path.startsWith("/css")
            || path.startsWith("/js")
            || path.startsWith("/images")
            || path.equals("/")
            || path.endsWith(".html");
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = request.getHeader("Authorization");
        System.out.println("🔐 Token header: " + token);

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);

            if (jwtUtil.validateJwtToken(token)) {
                String username = jwtUtil.getUsernameFromToken(token);
                List<String> roles = jwtUtil.getRolesFromToken(token);

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                User userEntity = userRepository.findByUsername(username).orElse(null);

                if (userEntity != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute("authenticatedUser", userEntity);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("User not found");
                    return;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Invalid or expired token");
                return;
            }
        } else {
            // 🚫 If no token is provided and it's a protected endpoint, deny access
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Missing Authorization header");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
