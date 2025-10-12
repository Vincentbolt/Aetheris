package com.aetheris.app.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aetheris.app.dto.StrategyRequest;
import com.aetheris.app.model.Strategy;
import com.aetheris.app.model.User;
import com.aetheris.app.security.CurrentUser;
import com.aetheris.app.service.StrategyService;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    @Autowired
    private StrategyService strategyService;

    /**
     * Create a new strategy
     */
    @PostMapping("/create")
    public ResponseEntity<?> createStrategy(@RequestBody StrategyRequest request,
                                            @AuthenticationPrincipal User user) {
        Strategy strategy = strategyService.createStrategy(request, user);
        return ResponseEntity.ok(strategy);
    }
    

    /**
     * Get all strategies for logged-in user
     */
    @GetMapping
    public ResponseEntity<List<Strategy>> getStrategies(@CurrentUser User user) {
        List<Strategy> strategies = strategyService.getStrategiesForUser(user);
        return ResponseEntity.ok(strategies);
    }

    /**
     * Update a strategy
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStrategy(@PathVariable Long id,
                                            @RequestBody Strategy strategy,
                                            @CurrentUser User user) {
        Strategy updated = strategyService.updateStrategy(id, strategy, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a strategy
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStrategy(@PathVariable Long id,
                                            @CurrentUser User user) {
        strategyService.deleteStrategy(id, user);
        return ResponseEntity.ok("Strategy deleted successfully");
    }
    
    /**
     * List all strategies for the logged-in user
     */
    @GetMapping("/list")
    public List<Strategy> listUserStrategies(@AuthenticationPrincipal User user) {
        return strategyService.getStrategiesForUser(user);
    }
}