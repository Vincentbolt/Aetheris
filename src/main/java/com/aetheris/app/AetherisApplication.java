package com.aetheris.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.aetheris.app")
public class AetherisApplication {
	public static void main(String[] args) {
	    try {
	        SpringApplication.run(AetherisApplication.class, args);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}

}