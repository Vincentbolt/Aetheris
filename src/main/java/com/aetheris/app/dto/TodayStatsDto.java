package com.aetheris.app.dto;

public class TodayStatsDto {
    private Double totalProfitLoss;
    private Double capital;
    private Integer totalTrades;
    private Integer winCount;
    private Double winRatePercent;

    // Constructor
    public TodayStatsDto(Double totalProfitLoss, Double capital, Integer totalTrades, Integer winCount, Double winRatePercent) {
        this.totalProfitLoss = totalProfitLoss;
        this.capital = capital;
        this.totalTrades = totalTrades;
        this.winCount = winCount;
        this.winRatePercent = winRatePercent;
    }
    
    public Double getTotalProfitLoss() {
        return totalProfitLoss;
    }

    public void setTotalProfitLoss(Double totalProfitLoss) {
        this.totalProfitLoss = totalProfitLoss;
    }

    public Double getCapital() {
        return capital;
    }

    public void setCapital(Double capital) {
        this.capital = capital;
    }

    public Integer getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(Integer totalTrades) {
        this.totalTrades = totalTrades;
    }

    public Integer getWinCount() {
        return winCount;
    }

    public void setWinCount(Integer winCount) {
        this.winCount = winCount;
    }

    public Double getWinRatePercent() {
        return winRatePercent;
    }

    public void setWinRatePercent(Double winRatePercent) {
        this.winRatePercent = winRatePercent;
    }
}

