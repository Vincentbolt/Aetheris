package com.aetheris.app.controller;

import com.aetheris.app.dto.LoginRequest;
import com.aetheris.app.dto.RegisterRequest;
import com.aetheris.app.model.Role;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.RoleRepository;
import com.aetheris.app.repo.UserRepository;
import com.aetheris.app.security.JwtUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;


// open endpoints for register/login under /api/auth
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    @Autowired
    private JwtUtils jwtUtils;

    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()) != null) {
            return ResponseEntity.badRequest().body("Email already in use.");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username already exists.");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        // Use mutable set instead of Collections.singleton
        Set<Role> roles = new HashSet<>();
        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default role not found, please initialize roles."));
        roles.add(defaultRole);

        // Optional bot role
        roleRepository.findByName("ROLE_BOT").ifPresent(roles::add);

        user.setRoles(roles);
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }


    
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().body("Password cannot be empty");
        }

        Optional<User> optionalUser = userRepository.findByUsername(request.getUsername());

        if (!optionalUser.isPresent() || optionalUser.get().getPassword() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        User user = optionalUser.get();
        
        if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        	// Convert roles to GrantedAuthority
        	List<SimpleGrantedAuthority> authorities = user.getRoles()
        	        .stream()
        	        .map(role -> new SimpleGrantedAuthority(role.getName()))
        	        .collect(Collectors.toList());

        	// ✅ Add ROLE_BOT if not present
        	if (authorities.stream().noneMatch(a -> a.getAuthority().equals("ROLE_BOT"))) {
        	    authorities.add(new SimpleGrantedAuthority("ROLE_BOT"));
        	}

        	String token = jwtUtils.generateToken(user.getUsername(), authorities);

        	Map<String, Object> response = new HashMap<>();
        	response.put("token", "Bearer " + token);
        	response.put("roles", authorities);


            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }
    }


}
