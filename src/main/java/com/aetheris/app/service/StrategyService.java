package com.aetheris.app.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aetheris.app.dto.StrategyRequest;
import com.aetheris.app.model.Strategy;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.StrategyRepository;
import com.aetheris.app.repo.UserRepository;

@Service
public class StrategyService {

    @Autowired
    private StrategyRepository strategyRepository;

    @Autowired
    private UserRepository userRepository;
    
    /**
     * Create a new strategy and link it to the logged-in user
     */
    public Strategy createStrategy(StrategyRequest request, User user) {
        Strategy strategy = new Strategy();
        strategy.setName(request.getName());
        strategy.setDescription(request.getDescription());
        strategy.setSymbol(request.getSymbol());
        strategy.setType(request.getType());
        strategy.setParameters(request.getParameters());
        strategy.setCreatedAt(LocalDateTime.now());
        User dbUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // link strategy to DB user
        strategy.setUser(dbUser);

        return strategyRepository.save(strategy);
    }

    /**
     * Get all strategies for a user
     */
    public List<Strategy> getStrategiesForUser(User user) {
        return strategyRepository.findByUser(user);
    }

    /**
     * Update an existing strategy
     */
    public Strategy updateStrategy(Long id, Strategy updatedStrategy, User user) {
        Strategy existing = strategyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Strategy not found"));

        if (!existing.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        existing.setName(updatedStrategy.getName());
        existing.setDescription(updatedStrategy.getDescription());
        existing.setSymbol(updatedStrategy.getSymbol());

        return strategyRepository.save(existing);
    }

    /**
     * Delete a strategy
     */
    public void deleteStrategy(Long id, User user) {
        Strategy existing = strategyRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Strategy not found"));

        if (!existing.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        strategyRepository.delete(existing);
    }
}
