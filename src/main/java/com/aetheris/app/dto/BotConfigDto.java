package com.aetheris.app.dto;

import com.aetheris.app.model.BotConfig;

public class BotConfigDto {

    private String variety;
    private String expiryDate;
    private String indexType;
    private Double capitalAmount;
    private Double targetPercent;
    private Double stoplossPercent;

    // Default constructor
    public BotConfigDto() {}

    // Constructor to initialize from BotConfig entity
    public BotConfigDto(BotConfig config) {
        this.variety = config.getVariety();
        this.expiryDate = config.getExpiryDate();
        this.indexType = config.getIndexType();
        this.capitalAmount = config.getCapitalAmount();
        this.targetPercent = config.getTargetPercent();
        this.stoplossPercent = config.getStoplossPercent();
    }

    // Getters and Setters
    public String getVariety() {
        return variety;
    }

    public void setVariety(String variety) {
        this.variety = variety;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public Double getCapitalAmount() {
        return capitalAmount;
    }

    public void setCapitalAmount(Double capitalAmount) {
        this.capitalAmount = capitalAmount;
    }

    public Double getTargetPercent() {
        return targetPercent;
    }

    public void setTargetPercent(Double targetPercent) {
        this.targetPercent = targetPercent;
    }

    public Double getStoplossPercent() {
        return stoplossPercent;
    }

    public void setStoplossPercent(Double stoplossPercent) {
        this.stoplossPercent = stoplossPercent;
    }
}
