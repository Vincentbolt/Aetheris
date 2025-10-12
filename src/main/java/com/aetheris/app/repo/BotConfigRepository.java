package com.aetheris.app.repo;

import com.aetheris.app.model.BotConfig;
import com.aetheris.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BotConfigRepository extends JpaRepository<BotConfig, Long> {
    Optional<BotConfig> findByUser(User user);
}