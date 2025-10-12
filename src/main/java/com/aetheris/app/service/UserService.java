package com.aetheris.app.service;

import com.aetheris.app.model.User;
import com.aetheris.app.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User register(String username, String email, String password) throws Exception {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new Exception("Username already exists");
        }
        if (userRepository.findByEmail(email) != null) {
            throw new Exception("Email already exists");
        }
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(hash(password));
        u.setApiToken(generateToken());
        return userRepository.save(u);
    }


    public User login(String usernameOrEmail, String password) throws Exception {
        // Use Optional correctly
        Optional<User> optionalUser = userRepository.findByUsername(usernameOrEmail);
        User u = optionalUser.orElseGet(() -> userRepository.findByEmail(usernameOrEmail));

        if (u == null) {
            throw new Exception("User not found");
        }
        if (!u.getPassword().equals(hash(password))) {
            throw new Exception("Invalid credentials");
        }

        // generate a fresh token on login
        u.setApiToken(generateToken());
        return userRepository.save(u);
    }


    public User findByToken(String token) {
        if (token == null) return null;
        return userRepository.findByApiToken(token);
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private String hash(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashed) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
