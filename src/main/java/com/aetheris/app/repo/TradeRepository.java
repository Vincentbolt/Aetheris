package com.aetheris.app.repo;


import com.aetheris.app.model.Trade;
import com.aetheris.app.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

	List<Trade> findByUser(User user);

    List<Trade> findByUserAndCreatedAtBetween(User user, LocalDateTime start, LocalDateTime end);
    
    List<Trade> findByUserAndEntryTimeAfter(User user, LocalDateTime entryTime);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.user = :user AND t.createdAt BETWEEN :start AND :end")
    int countTodayTrades(User user, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.user = :user AND t.profitOrLossPercent > 0 AND t.createdAt BETWEEN :start AND :end")
    int countWinningTrades(User user, LocalDateTime start, LocalDateTime end);
}

