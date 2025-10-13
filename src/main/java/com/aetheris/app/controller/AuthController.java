package com.aetheris.app.controller;

import com.aetheris.app.dto.LoginRequest;
import com.aetheris.app.dto.RegisterRequest;
import com.aetheris.app.model.ApiSettings;
import com.aetheris.app.model.Role;
import com.aetheris.app.model.Trade;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.RoleRepository;
import com.aetheris.app.repo.SettingsRepository;
import com.aetheris.app.repo.TradeRepository;
import com.aetheris.app.repo.UserRepository;
import com.aetheris.app.security.JwtUtils;
import com.aetheris.app.service.TradingAetherBotService;
import com.angelbroking.smartapi.SmartConnect;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private SettingsRepository settingsRepository;
    
    @Autowired
    private TradingAetherBotService botService;
    
    @Autowired
    private TradeRepository tradeRepository;
    
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    
    @Value("${app.mock-rms:false}") // default to false
    private boolean mockRms;
    
    
    
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
        
        // Check if the user has any trade records
        List<Trade> trades = tradeRepository.findByUser(user);
        if (trades == null || trades.isEmpty()) {
        	Trade trade = new Trade();
        	trade.setUser(user);
        	trade.setIndexOptionName("DEFAULT"); // or empty string
        	trade.setEntryPrice(0.0);
        	trade.setExitPrice(0.0);
        	trade.setAvailableCash(0.0);
        	trade.setProfitOrLossPercent(0.0);
        	trade.setEntryTime(null);
        	trade.setExitTime(null);
        	trade.setCreatedAt(LocalDateTime.now(IST));

        	tradeRepository.save(trade);
        }
        
        ApiSettings settings = settingsRepository.findByUser(user).orElse(null);
        if (settings != null) {
        	String totpKey = settings.getTotpKey();
        	String apiKey = settings.getApiKey();
        	String clientId = settings.getClientId();
        	String password = settings.getPassword();
        	double availableCash = 0.0;
        	
        	if (mockRms) {
        	    // Use dummy value in development
        	    availableCash = 0.0;
        	} else {
        	    SmartConnect smartConnect = botService.createSmartConnect(totpKey, apiKey, clientId, password);
        	    JSONObject response = smartConnect.getRMS();

        	    if (response != null && response.has("availablecash") && !response.isNull("availablecash")) {
        	        availableCash = Double.parseDouble(response.getString("availablecash"));
        	    }
        	}
        	List<Trade> existingTrades = tradeRepository.findByUser(user);
        	Trade tradeExist = existingTrades.get(0);
        	tradeExist.setAvailableCash(availableCash);
        	tradeRepository.save(tradeExist);
        }
        
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
