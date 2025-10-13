package com.aetheris.app.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.aetheris.app.service.TradingAetherBotService;

@RestController
@RequestMapping("/api/bot")
public class TradingBotController {

    private final TradingAetherBotService botService;

    public TradingBotController(TradingAetherBotService botService) {
        this.botService = botService;
    }

    @PostMapping("/config")
    public ResponseEntity<String> setConfig(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok("Config updated");
    }

    @PostMapping("/start")
    public ResponseEntity<String> startBot(@RequestParam Long  userId) {
    	System.out.println("Trade Bot Started");
    	boolean started = botService.startBot(userId); // return true if actually started, false if already running

        if (!started) {
            return ResponseEntity.status(409).body("⚠️ Bot is already running");
        }
        
        return ResponseEntity.ok("Bot started");
    }
    
    @PostMapping("/stop")
    public ResponseEntity<String> stopBot() {
    	System.out.println("Trade Bot Stopped");
        botService.stopBot();
        return ResponseEntity.ok("Bot stopped");
    }

    @GetMapping("/status")
    public ResponseEntity<TradingAetherBotService.BotStatus> getStatus() {
        return ResponseEntity.ok(botService.getBotStatus());
    }
}

