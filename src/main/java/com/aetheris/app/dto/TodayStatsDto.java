package com.aetheris.app.dto;

public class TodayStatsDto {
    private Double totalProfitLoss;
    private Double capital;
    private Integer totalTrades;
    private Integer winCount;
    private Double winRatePercent;
    private Double availableCash;

    // Constructor
    public TodayStatsDto(Double totalProfitLoss, Double capital, Integer totalTrades, Integer winCount, Double winRatePercent, Double availableCash) {
        this.totalProfitLoss = totalProfitLoss;
        this.capital = capital;
        this.totalTrades = totalTrades;
        this.winCount = winCount;
        this.winRatePercent = winRatePercent;
        this.availableCash = availableCash;
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

	/**
	 * @return the availableCash
	 */
	public Double getAvailableCash() {
		return availableCash;
	}

	/**
	 * @param availableCash the availableCash to set
	 */
	public void setAvailableCash(Double availableCash) {
		this.availableCash = availableCash;
	}
}

