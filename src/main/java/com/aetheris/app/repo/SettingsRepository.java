package com.aetheris.app.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aetheris.app.model.ApiSettings;
import com.aetheris.app.model.User;

import java.util.Optional;

public interface SettingsRepository extends JpaRepository<ApiSettings, Long> {
	Optional<ApiSettings> findByUser(User user);
}

