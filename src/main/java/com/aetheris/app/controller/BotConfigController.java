package com.aetheris.app.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aetheris.app.model.BotConfig;
import com.aetheris.app.dto.BotConfigDto;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.BotConfigRepository;
import com.aetheris.app.repo.UserRepository;

@RestController
@RequestMapping("/api/bot-config")
public class BotConfigController {

    @Autowired
    private BotConfigRepository botConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/save")
    public ResponseEntity<?> saveConfig(@RequestBody BotConfigDto dto, Principal principal) {
        // Find the user
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Retrieve or create a new BotConfig
        BotConfig config = botConfigRepository.findByUser(user).orElse(new BotConfig());
        config.setUser(user);
        config.setVariety(dto.getVariety());
        config.setExpiryDate(dto.getExpiryDate());
        config.setIndexType(dto.getIndexType());
        config.setCapitalAmount(dto.getCapitalAmount());
        config.setTargetPercent(dto.getTargetPercent());
        config.setStoplossPercent(dto.getStoplossPercent());

        // Save the BotConfig
        botConfigRepository.save(config);

        // Use Collections.singletonMap for Java 8 compatibility
        return ResponseEntity.ok(Collections.singletonMap("message", "Bot config saved successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getConfig(Principal principal) {
        // Find the user
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Retrieve the BotConfig, if present
        Optional<BotConfig> configOpt = botConfigRepository.findByUser(user);

        if (!configOpt.isPresent()) {
            // Return empty DTO instead of null
            return ResponseEntity.ok(new BotConfigDto());
        }

        // Return the BotConfigDto if found
        BotConfig config = configOpt.get();
        return ResponseEntity.ok(new BotConfigDto(config));
    }

}
