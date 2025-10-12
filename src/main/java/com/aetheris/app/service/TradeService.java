package com.aetheris.app.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aetheris.app.dto.TodayStatsDto;
import com.aetheris.app.dto.TradeRequest;
import com.aetheris.app.dto.TradeSummaryDto;
import com.aetheris.app.model.Order;
import com.aetheris.app.model.Strategy;
import com.aetheris.app.model.Trade;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.OrderRepository;
import com.aetheris.app.repo.StrategyRepository;
import com.aetheris.app.repo.TradeRepository;
import com.aetheris.app.repo.UserRepository;

@Service
public class TradeService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StrategyRepository strategyRepository;
    
    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Execute a trade based on a strategy.
     */
    public Order executeTrade(TradeRequest request, User user) {
        Strategy strategy = strategyRepository.findById(request.getStrategyId())
                .orElseThrow(() -> new RuntimeException("Strategy not found"));

        if (!strategy.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        Order order = new Order();
        order.setSymbol(request.getSymbol());
        order.setQuantity(request.getQuantity());
        order.setPrice(request.getPrice());
        order.setType(request.getType());
        order.setStatus("EXECUTED");
        order.setStrategy(strategy);
        order.setTimestamp(LocalDateTime.now());

        return orderRepository.save(order);
    }

    /**
     * Fetch all orders for a user
     */
    public List<Order> getOrdersForUser(User user) {
        return orderRepository.findByStrategyUser(user);
    }
    
    public List<TradeSummaryDto> getTodayTrades(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Start of today
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        return tradeRepository.findByUserAndEntryTimeAfter(user, startOfToday)
                .stream()
                .map(TradeSummaryDto::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<TradeSummaryDto> getTotalTradesForCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fetch ALL trades for the user (no date filter)
        return tradeRepository.findByUser(user)
                .stream()
                .map(TradeSummaryDto::fromEntity)
                .collect(Collectors.toList());
    }


    
    public TodayStatsDto getTodayStats(Long userId) {
    	User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0);
        LocalDateTime end = LocalDateTime.now().withHour(23).withMinute(59);

        List<Trade> trades = tradeRepository.findByUserAndCreatedAtBetween(user, start, end);
        double totalPnl = trades.stream()
            .mapToDouble(t -> ((t.getExitPrice() - t.getEntryPrice()) / t.getEntryPrice()) * 100)
            .sum();

        int totalTrades = trades.size();
        int winTrades = (int) trades.stream().filter(t -> t.getProfitOrLossPercent() > 0).count();
        double winRate = totalTrades == 0 ? 0 : ((double) winTrades / totalTrades) * 100;

        return new TodayStatsDto(totalPnl, user.getCapital(), totalTrades, winTrades, winRate);
    }
}