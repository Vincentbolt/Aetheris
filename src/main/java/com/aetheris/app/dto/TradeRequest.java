package com.aetheris.app.dto;

public class TradeRequest {
    private Long strategyId;
    private String symbol;
    private Double quantity;
    private Double price;
    private String type; // BUY/SELL
	/**
	 * @return the strategyId
	 */
	public Long getStrategyId() {
		return strategyId;
	}
	/**
	 * @param strategyId the strategyId to set
	 */
	public void setStrategyId(Long strategyId) {
		this.strategyId = strategyId;
	}
	/**
	 * @return the symbol
	 */
	public String getSymbol() {
		return symbol;
	}
	/**
	 * @param symbol the symbol to set
	 */
	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}
	/**
	 * @return the quantity
	 */
	public Double getQuantity() {
		return quantity;
	}
	/**
	 * @param quantity the quantity to set
	 */
	public void setQuantity(Double quantity) {
		this.quantity = quantity;
	}
	/**
	 * @return the price
	 */
	public Double getPrice() {
		return price;
	}
	/**
	 * @param price the price to set
	 */
	public void setPrice(Double price) {
		this.price = price;
	}
	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}
	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}
}

