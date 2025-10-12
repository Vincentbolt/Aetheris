package com.aetheris.app.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aetheris.app.dto.ApiSettingsDto;
import com.aetheris.app.model.ApiSettings;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.SettingsRepository;
import com.aetheris.app.repo.UserRepository;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	@Autowired
    private UserRepository userRepository;
	
    @Autowired
    private SettingsRepository settingsRepository;

    @PostMapping("/save")
    public ResponseEntity<?> saveSettings(@RequestBody ApiSettingsDto settingsDto, Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                      .orElseThrow(() -> new RuntimeException("User not found"));

        // fetch existing settings by user
        ApiSettings settings = settingsRepository.findByUser(user)
                .orElse(new ApiSettings());

        settings.setUser(user);  // link to User
        settings.setUsername(user.getUsername());
        settings.setApiKey(settingsDto.getApiKey());
        settings.setClientId(settingsDto.getClientId());
        settings.setTotpKey(settingsDto.getTotpKey());
        settings.setPassword(settingsDto.getPassword());

        settingsRepository.save(settings);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Settings saved successfully");

        return ResponseEntity.ok(response);
    }

    
    // Endpoint to fetch settings for logged-in user
    @GetMapping("/me")
    public ResponseEntity<?> getSettings(Principal principal) {
        User user = userRepository.findByUsername(principal.getName())
                        .orElseThrow(() -> new RuntimeException("User not found"));

        ApiSettings settings = settingsRepository.findByUser(user).orElse(null);

        if (settings == null) return ResponseEntity.ok(null);

        // Use DTO, not entity
        ApiSettingsDto dto = new ApiSettingsDto();
        dto.setApiKey(settings.getApiKey());
        dto.setClientId(settings.getClientId());
        dto.setTotpKey(settings.getTotpKey());
        dto.setPassword(settings.getPassword());

        return ResponseEntity.ok(dto);
    }
    
}
