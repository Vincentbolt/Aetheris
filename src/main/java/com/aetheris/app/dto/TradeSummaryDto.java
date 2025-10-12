package com.aetheris.app.dto;

import com.aetheris.app.model.Trade;
import java.time.LocalDateTime;

public class TradeSummaryDto {
    private String indexOptionName;
    private Double entryPrice;
    private Double exitPrice;
    private LocalDateTime entryTime;
    private Double profitOrLossPercent;

    // Constructor
    public TradeSummaryDto(String indexOptionName, Double entryPrice, Double exitPrice, LocalDateTime entryTime, Double profitOrLossPercent) {
        this.indexOptionName = indexOptionName;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.entryTime = entryTime;
        this.profitOrLossPercent = profitOrLossPercent;
    }

    // Static factory method to map from entity
    public static TradeSummaryDto fromEntity(Trade trade) {
        return new TradeSummaryDto(
            trade.getIndexOptionName(),
            trade.getEntryPrice(),
            trade.getExitPrice(),
            trade.getEntryTime(),
            trade.getProfitOrLossPercent()
        );
    }


    // Getters and setters
    public String getIndexOptionName() {
        return indexOptionName;
    }

    public void setIndexOptionName(String indexOptionName) {
        this.indexOptionName = indexOptionName;
    }

    public Double getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(Double entryPrice) {
        this.entryPrice = entryPrice;
    }

    public Double getExitPrice() {
        return exitPrice;
    }

    public void setExitPrice(Double exitPrice) {
        this.exitPrice = exitPrice;
    }

    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(LocalDateTime entryTime) {
        this.entryTime = entryTime;
    }

    public Double getProfitOrLossPercent() {
        return profitOrLossPercent;
    }

    public void setProfitOrLossPercent(Double profitOrLossPercent) {
        this.profitOrLossPercent = profitOrLossPercent;
    }
}