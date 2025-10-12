package com.aetheris.app.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.aetheris.app.dto.TodayStatsDto;
import com.aetheris.app.dto.TradeRequest;
import com.aetheris.app.dto.TradeSummaryDto;
import com.aetheris.app.model.Order;
import com.aetheris.app.model.User;
import com.aetheris.app.security.CurrentUser;
import com.aetheris.app.service.TradeService;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    @Autowired
    private TradeService tradeService;

    /**
     * Execute a trade
     */
    @PostMapping("/execute")
    public ResponseEntity<Order> executeTrade(@RequestBody TradeRequest request,
                                              @CurrentUser User user) {
        Order order = tradeService.executeTrade(request, user);
        return ResponseEntity.ok(order);
    }

    /**
     * Get all orders for logged-in user
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders(@CurrentUser User user) {
        List<Order> orders = tradeService.getOrdersForUser(user);
        return ResponseEntity.ok(orders);
    }
    
    /**
     * List all trades/orders for the logged-in user
     */
    @GetMapping("/list")
    public List<Order> listUserOrders(@AuthenticationPrincipal User user) {
        return tradeService.getOrdersForUser(user);
    }
    
    @GetMapping("/today")
    public ResponseEntity<List<TradeSummaryDto>> getTodayTrades(@RequestParam Long userId,
                                                                @RequestHeader("Authorization") String authHeader) {
        System.out.println("Auth Header: " + authHeader);
        return ResponseEntity.ok(tradeService.getTodayTrades(userId));
    }


    @GetMapping("/today/stats")
    public ResponseEntity<TodayStatsDto> getTodayStats(@RequestParam Long userId) {
        return ResponseEntity.ok(tradeService.getTodayStats(userId));
    }
    
    @GetMapping("/all")
    public ResponseEntity<List<TradeSummaryDto>> getAllTradesForCurrentUser(@RequestParam Long userId) {
        return ResponseEntity.ok(tradeService.getTotalTradesForCurrentUser(userId));
    }

    
}