package com.aetheris.app.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import com.angelbroking.smartapi.SmartConnect;
import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
import com.angelbroking.smartapi.models.Order;
import com.angelbroking.smartapi.models.OrderParams;

@Service
public class AetherBotHelper {

	private static final Logger logger = LoggerFactory.getLogger(AetherBotHelper.class);
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static IndicatorServices indHelper = new IndicatorServices();
	
	
	public String getOptionType(double secondHigh, double firstHigh, double secondLow, double firstLow, double secondOpen, double firstOpen, double secondClose, double firstClose) {
		String optionType = null;
		if (secondHigh > firstHigh && secondLow > firstLow ) {//&& secondOpen >= firstOpen && secondClose > firstClose
			optionType = "CE";
		} else if (secondLow < firstLow && secondHigh < firstHigh ) {//&& secondOpen <= firstOpen && secondClose < firstClose
			optionType = "PE";
		}
		return optionType;
	}

	
	
	public List<Double> getInitialIndexCloses(JSONArray response1) {
		List<Double> closes = new ArrayList<>();
		for (int i = response1.length() - 6; i < response1.length(); i++) {
			JSONArray candle = response1.getJSONArray(i);
			double close = candle.getDouble(4);
			closes.add(close);
		}
		return closes;
	}

	
	
	public int getRoundedValue(String indexType) {
		int roundTo = 0;
		if ("MIDCPNIFTY".equalsIgnoreCase(indexType)) {
			roundTo = 25;
		} else {
			roundTo = ("SENSEX".equalsIgnoreCase(indexType) || "BANKNIFTY".equalsIgnoreCase(indexType)) ? 100 : 50;
		}
		return roundTo;
	}

	
	
	public boolean isCandleDataValid(JSONArray response) {
	    if (response == null || response.length() < 7) {
	        if (response != null) {
	            logger.info("Not enough candles. Candles Formed: {}", response.length());
	        } else {
	            logger.info("Not enough candles.");
	        }
	        return false;
	    }
	    return true;
	}

	
	
	public boolean isIndexRSIFilterPassed(String optionType, double rsi) {
	    if ((optionType.equals("CE") && rsi > 70) || (optionType.equals("PE") && rsi < 30)) {
	        logger.info("RSI filter failed: " + rsi);
	        return false;
	    }
	    return true;
	}
	
	
	
	public boolean isVWAPPassed(JSONArray optionData, double vwapOption, String niftyString) throws JSONException {
	    JSONArray optionFirstCandle = optionData.getJSONArray(optionData.length() - 2);
	    JSONArray optionSecondCandle = optionData.getJSONArray(optionData.length() - 1);
	    
	    logger.info("optionFirstCandle : {}",optionFirstCandle);
	    logger.info("optionSecondCandle : {}",optionSecondCandle);
	    
	    
	    double optionFirstHigh = optionFirstCandle.getDouble(2);
	    double optionFirstLow = optionFirstCandle.getDouble(3);
	    double optionSecondHigh = optionSecondCandle.getDouble(1);
	    double optionSecondLow = optionSecondCandle.getDouble(3);
	    double optionSecondClose = optionSecondCandle.getDouble(4);

	    if (optionSecondHigh > optionFirstHigh && optionSecondLow >= optionFirstLow ) {//&& optionSecondOpen >= optionFirstOpen && optionSecondClose > optionFirstClose
	        if (optionSecondClose <= vwapOption) {
	            logger.info("Option VWAP filter failed.");
	            return false;
	        }
	        return true;
	    } else {
	    	logger.info("optionSecondHigh:  Less than {}, optionFirstHigh:  greater then {}, OptionName: {}", optionSecondHigh, optionFirstHigh, niftyString);
	    	logger.info("optionSecondLow:  Less than {}, optionFirstLow:  greater then {}, OptionName: {}", optionSecondLow, optionFirstLow, niftyString);
	        logger.info("Basic Trend pattern not formed.");
	        return false;
	    }
	}

	public JSONArray fetchCandleDataForLastTwo(String exchange, String token, SmartConnect smartConnect) {
	    JSONObject request = new JSONObject();
	    request.put("exchange", exchange);
	    request.put("symboltoken", token);
	    request.put("interval", "FIVE_MINUTE");

	    // Get current time rounded down to the previous 5-minute mark
	    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
	    int minutesRounded = (now.getMinute() / 5) * 5;
	    LocalDateTime endTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), minutesRounded));

	    // If current time is exactly on a 5-min boundary (e.g., 12:20:00), we need to move one interval back
	    if (now.getMinute() % 5 == 0 && now.getSecond() == 0) {
	        endTime = endTime.minusMinutes(5);
	    }

	    // Start time is 10 minutes before end time (2 candles of 5 minutes)
	    LocalDateTime startTime = endTime.minusMinutes(10);

	    // Add formatted time to request
	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	    request.put("fromdate", startTime.format(formatter));  // e.g., 12:10:00
	    request.put("todate", endTime.format(formatter));      // e.g., 12:20:00

	    try {
	        return smartConnect.candleData(request);
	    } catch (Exception e) {
	        logger.error("Error fetching candle data for token {}: {}", token, e.getMessage(), e);
	        return null;
	    }
	}

	
	public boolean isOptionRSIFilterPassed(double rsiOption) {
	    if (rsiOption < 50 || !(rsiOption >= 75)) {
	        logger.info("Option RSI filter failed: {}", rsiOption);
	        return false;
	    }
	    return true;
	}

	
	
	public boolean isLiquidityValid(JSONObject marketData, double ltp) {
	    try {
	        JSONObject fetchedData = marketData.getJSONArray("fetched").getJSONObject(0);
	        double volume = fetchedData.getDouble("tradeVolume");

	        JSONArray buyDepth = fetchedData.getJSONObject("depth").getJSONArray("buy");
	        JSONArray sellDepth = fetchedData.getJSONObject("depth").getJSONArray("sell");

	        double bid = buyDepth.getJSONObject(0).getDouble("price");
	        double ask = sellDepth.getJSONObject(0).getDouble("price");

	        double spread = ask - bid;
	        double expectedEntry = (ask + bid) / 2;

	        if (volume < 100000) {
	            logger.info("Low liquidity. Volume: {}", volume);
	            return false;
	        }

	        if (spread / ltp > 0.01) {
	            logger.info("Spread too high: {}", spread);
	            return false;
	        }

	        if (Math.abs(expectedEntry - ltp) / ltp > 0.005) {
	            logger.info("High slippage. Expected Entry: ₹{}", expectedEntry);
	            return false;
	        }
	        return true;

	    } catch (JSONException e) {
	        logger.error("Error parsing market depth data: ", e);
	        return false;
	    }
	}

	
	
	public boolean isIVValid(double iv) {
	    if (iv < 10 || iv > 70) {
	        logger.info("IV out of range: {}", iv);
	        return false;
	    }
	    return true;
	}
	
	
	
	public boolean isVegaThetaValid(double ratio, String indexType) {
	    if (indexType.equalsIgnoreCase("SENSEX")) {
	        if (ratio < 1500) {
	            logger.info("Vega/Theta filter failed: {}", ratio);
	            return false;
	        }
	    } else {
	        if (ratio < 1.5) {
	            logger.info("Vega/Theta filter failed: {}", ratio);
	            return false;
	        }
	    }
	    return true;
	}

	
	
	public boolean isRiskRewardFavorable(double entry, double target, double stoploss) {
	    double risk = entry - stoploss;
	    double reward = target - entry;
	    if (reward / risk < 2.0) {
	        logger.info("Risk:Reward not favorable: {}", reward / risk);
	        return false;
	    }
	    return true;
	}

	
	
	public String getNSETokens(String indexType) {
		String token = null;
		switch (indexType) {
		case "MIDCPNIFTY":
			token = "99926074";
			break;
		case "BANKNIFTY":
			token = "99926009";
			break;
		case "NIFTY":
			token = "99926000";
			break;
		case "FINNIFTY":
			token = "99926037";
			break;
		case "SENSEX":
			token = "99919000";
			break;
		}
		return token;
	}
	
	
	
	public String getExchangeOption(String indexType) {
		String exchangeOption = "NFO";
		
		if (indexType.equals("SENSEX")) {
			exchangeOption = "BFO";
		}
		return exchangeOption;
	}
	
	
	
	public String getExchange(String indexType) {
		String exchange = "NSE";
		
		if (indexType.equals("SENSEX")) {
			exchange = "BSE";
		}
		return exchange;
	}

	
	
	public int getLotSize(String indexType) {
		int lotSize = 0;
		
		switch (indexType) {
		case "MIDCPNIFTY":
			lotSize = 140;
			break;
		case "BANKNIFTY":
			lotSize = 35;
			break;
		case "NIFTY":
			lotSize = 75;
			break;
		case "FINNIFTY":
			lotSize = 65;
			break;
		case "SENSEX":
			lotSize = 20;
			break;
		}
		return lotSize;
	}
	
	
	
	public JSONArray fetchCandleData(String exchange, String token, SmartConnect smartConnect, long minutes) {
		JSONObject request = new JSONObject();
		request.put("exchange", exchange);
		request.put("symboltoken", token);
		request.put("interval", "FIVE_MINUTE");

		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
		int minutesRounded = (now.getMinute() / 5) * 5;
		LocalDateTime toTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), minutesRounded));
		LocalDateTime fromTime = toTime.minusMinutes(35);

		
		request.put("fromdate", fromTime.format(formatter));
		request.put("todate", toTime.format(formatter));

		try {
			return smartConnect.candleData(request);
		} catch (Exception e) {
			logger.error("Error fetching candle data for token {}: {}", token, e.getMessage(), e);
			return null;
		}
	}
	
	
	
	public JSONObject forFetchAskAndBidPrice(String token, SmartConnect smartConnect,String exchangeOption) {
		JSONObject payload = new JSONObject();
        payload.put("mode", "FULL"); // You can change the mode as needed
        JSONObject exchangeTokens = new JSONObject();
        JSONArray nseTokens = new JSONArray();
        nseTokens.put(token);
        exchangeTokens.put(exchangeOption, nseTokens);
        payload.put("exchangeTokens", exchangeTokens);
        JSONObject response = null;
        try {
			response = smartConnect.marketData(payload);
			logger.info("Latest Bid and Ask: {}", response);
		} catch (IOException | SmartAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return response;
	}
	
	
	
	public JSONArray fetchNiftyTradeValue(String exchange, String token, SmartConnect smartConnect, long minutes) throws InterruptedException {
		int retries = 3;
		while (retries-- > 0) {
			JSONArray data = fetchCandleData(exchange, token, smartConnect, minutes);
			if (data != null && data.length() > 0) return data;
			Thread.sleep(2000);
		}
		return null;
	}
	
	
	
	public JSONArray fetchNiftyValue(String exchangeOption, String token, SmartConnect smartConnect, long minutes) {
		return fetchCandleData(exchangeOption, token, smartConnect, minutes);
	}
	
	public void profitHit(String orderId, String niftyString, double atTheTimeOption, double marketPr, double targetValue, double stoplossValue, int quantity, double capitalUsed) {
		double profitlossValue = marketPr - atTheTimeOption;
		String stringValue = "PROFIT : " + Math.round(profitlossValue * (double)quantity);

		logger.info("Target Hit. Entry Price: {} | Exit Price: {}", atTheTimeOption, marketPr);
		//TradingBotUtil.sendTelegramMessage("🎯 Target Hit!\n" + "Symbol: " + niftyString + "\nEntry: ₹" + atTheTimeOption + "\nExit: ₹" + marketPr + "\n From AetherBot");	
	}
	
	public void StopLossHit(String orderId, String niftyString, double atTheTimeOption, double marketPr, double targetValue, double stoplossValue, int quantity, double capitalUsed) {
		double profitlossValue = atTheTimeOption - marketPr;
		String stringValue = "LOSS : " + Math.round(profitlossValue * (double)quantity); 

		logger.info("Stoploss Hit. Entry Price: {} | Exit Price: {}", atTheTimeOption, marketPr);
		//TradingBotUtil.sendTelegramMessage("❌ Stoploss Hit!\n" + "Symbol: " + niftyString + "\nEntry: ₹" + atTheTimeOption + "\nExit: ₹" + marketPr + "\n From AetherBot");	
	}
	
	
	public String placeOrder(String niftyString, String token, String transactionType, double price, int quantity, double target, double stoploss,
			SmartConnect smartConnect, String VARIETY_REGULAR, String exchangeOption, String ORDER_TYPE_LIMIT, String PRODUCT_MIS, String VALIDITY_DAY) {
		
		try {
			OrderParams orderParams = new OrderParams();
			orderParams.variety = VARIETY_REGULAR;
			orderParams.tradingsymbol = niftyString;
			orderParams.symboltoken = token;
			orderParams.transactiontype = transactionType;
			orderParams.exchange = exchangeOption;
			orderParams.ordertype = ORDER_TYPE_LIMIT;
			orderParams.producttype = PRODUCT_MIS;
			orderParams.duration = VALIDITY_DAY;
			orderParams.price = price;
			orderParams.quantity = quantity;
			orderParams.triggerprice = String.valueOf(price);
			orderParams.squareoff = String.valueOf(target);
			orderParams.stoploss = String.valueOf(stoploss);

			int attempts = 3;
		    while (attempts-- > 0) {
		        try {
		            Order order = smartConnect.placeOrder(orderParams, VARIETY_REGULAR);
		            logger.info("Buy order placed successfully. Order ID: {}", order.orderId);
		            return order.orderId;
		        } catch (Exception e) {
		            logger.warn("Failed to place order. Retrying... Attempts left: {}", attempts);
		            Thread.sleep(2000);
		        }
		    }

		} catch (Exception e) {
			logger.error("Error in placeOrder: ", e);
		}
		return null;
	}
	
}
