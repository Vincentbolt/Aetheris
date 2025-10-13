package com.aetheris.app.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

	@GetMapping("/secure")
	public String secureEndpoint(Authentication authentication) {
	    if (authentication != null && authentication.isAuthenticated()) {
	        String username = authentication.getName(); // now it contains username from JWT
	        return "Hello " + username + "! You have accessed a protected endpoint.";
	    } else {
	        return "Unauthorized access";
	    }
	}


    @GetMapping("/public")
    public String publicEndpoint() {
        return "This is a public endpoint. No token required.";
    }
}
