package com.aetheris.app.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class DashboardController {

	@GetMapping("/api/secure/dashboard")
	public ResponseEntity<?> dashboard(Authentication authentication) {
	    if (authentication == null || !authentication.isAuthenticated()) {
	        return ResponseEntity.status(401).body(Collections.singletonMap("error", "unauthenticated"));
	    }

	    String username = authentication.getName();

	    Map<String, Object> res = new HashMap<>();
	    res.put("username", username);
	    res.put("capital", 100000); // Replace with actual user capital
	    res.put("preferredIndex", "NIFTY"); // Replace with actual preference

	    // Sample trades
	    List<Map<String, Object>> trades = new ArrayList<>();
	    Map<String, Object> t1 = new HashMap<>();
	    t1.put("symbol", "NIFTY 21500 CE");
	    t1.put("price", 120.5);
	    t1.put("qty", 50);
	    t1.put("pl", 320.0);
	    trades.add(t1);
	    res.put("recentTrades", trades);

	    // Sample chart
	    List<Map<String, Object>> points = new ArrayList<>();
	    for (int i = 0; i < 7; i++) {
	        Map<String, Object> p = new HashMap<>();
	        p.put("day", "D" + (i + 1));
	        p.put("value", 1000 + i * 50 + (int)(Math.random()*200));
	        points.add(p);
	    }
	    res.put("chart", points);

	    return ResponseEntity.ok(res);
	}

}
