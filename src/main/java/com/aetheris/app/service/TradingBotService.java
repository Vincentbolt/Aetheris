//package com.aetheris.app.service;
//
//import java.text.SimpleDateFormat;
//import java.time.LocalDateTime;
//import java.time.LocalTime;
//import java.time.format.DateTimeFormatter;
//import java.time.temporal.ChronoUnit;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import org.json.JSONArray;
//import org.json.JSONObject;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.stereotype.Service;
//import org.springframework.web.bind.annotation.GetMapping;
//
//import com.angelbroking.smartapi.SmartConnect;
//import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
//
//@Service
//public class TradingBotService {
//
//	private static final Logger logger = LoggerFactory.getLogger(TradingBotService.class);
//    private final IndicatorServices indServices;
//    private final AetherBotHelper botHelper;
//
//    // 🔹 Add these
//    private String exchange = "NSE";             // Default underlying exchange
//    private String exchangeOption = "NFO";       // Default option exchange
//    private String token;                         // Underlying token
//    private String niftyString;                   // Generated option symbol
//    private String optionToken;                   // Option token
//    private String indexType = "NIFTY";           // Nifty or Sensex
//    private String expiryDateWithMonthyear = "14OCT2025";  // Example expiry
//    private double capitalAmount = 500000;         // Example capital
//    private int lotSize = 75;                     // Example lot size
//    private long cooldownMillis = 0;
//    
//    //Only for SENSEX
//  	private static String expiryDateStr = "2025-10-09";
//    // 🔹 Constants
//    private static final String TRANSACTION_TYPE_BUY = "BUY";
//    private static final String VARIETY_REGULAR = "REGULAR";
//    private static final String ORDER_TYPE_LIMIT = "LIMIT";
//    private static final String PRODUCT_MIS = "MIS";
//    private static final String VALIDITY_DAY = "DAY";
//    private double targetPercent;
//    private double stoplossPercent;
//
//    private final AtomicBoolean positionTaken = new AtomicBoolean(false);
//    private final AtomicBoolean reached = new AtomicBoolean(false);
//    
//
//    public TradingBotService(SmartConnect smartConnect, IndicatorServices indServices, AetherBotHelper botHelper) {
//        this.smartConnect = smartConnect;
//        this.indServices = indServices;
//        this.botHelper = botHelper;
//    }
//
//    // The main bot logic
//    public void executeStrategy() {
//        try {
//        	lotSize = botHelper.getLotSize(indexType);
//            JSONArray niftyData = fetchNiftyTradeValue();
//            if (!isEnoughCandles(niftyData)) return;
//
//            // Step 1: Determine Trend
//            String optionType = detectTrend(niftyData);
//            if (optionType == null) {
//                cooldownMillis = 200_000L;
//                return;
//            }
//
//            // Step 2: RSI Filter on underlying
//            List<Double> niftyCloses = extractCloses(niftyData, 6);
//            if (!isRSIFilterPassed(niftyCloses, optionType)) return;
//
//            // Step 3: Fetch option token
//            double underlyingPrice = smartConnect.getLTP(exchange, exchangeOption, token).getDouble("ltp");
//            int roundTo = botHelper.getRoundedValue(indexType);
//            double strikePrice = Math.round(underlyingPrice / roundTo) * roundTo;
//            String strikeOption = ((int) strikePrice) + optionType;
//            niftyString = indServices.generateNiftyString(indexType, strikeOption, expiryDateWithMonthyear);
//            optionToken = indServices.getToken(niftyString);
//            if (optionToken == null) {
//                logger.error("Option token not generated for: {}", niftyString);
//                return;
//            }
//
//            // Step 4: Fetch option data
//            JSONArray optionData = fetchNiftyValue(optionToken);
//            if (!isEnoughCandles(optionData)) return;
//
//            OptionMetrics metrics = calculateOptionMetrics(optionData);
//            if (!metrics.isValid) return;
//
//            if (!botHelper.isOptionRSIFilterPassed(metrics.rsi)) {
//				cooldownMillis = 1_000L;
//				return;
//			}
//            
//            // Step 5: Calculate IV & Greeks
//            Greeks greeks = calculateGreeks(optionType, strikePrice, metrics.latestPrice);
//
//            // Step 6: Risk-Reward Check
//            double targetValue = metrics.latestPrice * (1 + targetPercent / 100);
//            double stoplossValue = metrics.latestPrice * (1 - stoplossPercent / 100);
//            if (!botHelper.isRiskRewardFavorable(metrics.latestPrice, targetValue, stoplossValue)) {
//                cooldownMillis = 1_000L;
//                return;
//            }
//
//            // Step 7: Place Order
//            int quantity = calculateQuantity(metrics.latestPrice);
//            String orderId = botHelper.placeOrder(
//                    niftyString, optionToken, TRANSACTION_TYPE_BUY, metrics.latestPrice,
//                    quantity, targetValue, stoplossValue, smartConnect, VARIETY_REGULAR,
//                    exchangeOption, ORDER_TYPE_LIMIT, PRODUCT_MIS, VALIDITY_DAY
//            );
//
//            // Step 8: Monitor Order
//            monitorOrder(orderId, metrics, targetValue, stoplossValue, quantity, greeks);
//
//        } catch (Exception e) {
//            logger.error("Error in executeStrategy: ", e);
//        }
//    }
//    
//    private boolean isEnoughCandles(JSONArray data) {
//        if (data == null || data.length() < 6) {
//            logger.info("Not enough candles. Candles formed: {}", data != null ? data.length() : 0);
//            cooldownMillis = 1_000L;
//            return false;
//        }
//        return true;
//    }
//
//    private String detectTrend(JSONArray data) {
//        JSONArray first = data.getJSONArray(data.length() - 2);
//        JSONArray second = data.getJSONArray(data.length() - 1);
//
//        double firstHigh = first.getDouble(2), firstLow = first.getDouble(3);
//        double secondHigh = second.getDouble(2), secondLow = second.getDouble(3);
//
//        if (secondHigh > firstHigh && secondLow > firstLow) return "CE";
//        if (secondLow < firstLow && secondHigh < firstHigh) return "PE";
//
//        logger.info("Basic trend pattern not formed.");
//        return null;
//    }
//
//    private List<Double> extractCloses(JSONArray data, int count) {
//        List<Double> closes = new ArrayList<>();
//        for (int i = data.length() - count; i < data.length(); i++) {
//            closes.add(data.getJSONArray(i).getDouble(4));
//        }
//        return closes;
//    }
//
//    private boolean isRSIFilterPassed(List<Double> closes, String optionType) {
//        double rsi = indServices.calculateRSI(closes, 5);
//        if ((optionType.equals("CE") && rsi > 70) || (optionType.equals("PE") && rsi < 30)) {
//            logger.info("RSI filter failed: {}", rsi);
//            cooldownMillis = 1_000L;
//            return false;
//        }
//        return true;
//    }
//
//    private OptionMetrics calculateOptionMetrics(JSONArray optionData) {
//        List<Double> closes = extractCloses(optionData, 6);
//        List<Double> highs = new ArrayList<>(), lows = new ArrayList<>(), volumes = new ArrayList<>();
//        double vwapNum = 0, vwapDen = 0;
//
//        for (int i = optionData.length() - 6; i < optionData.length(); i++) {
//            JSONArray c = optionData.getJSONArray(i);
//            double high = c.getDouble(2), low = c.getDouble(3), close = c.getDouble(4), volume = c.getDouble(5);
//            highs.add(high); lows.add(low); volumes.add(volume); closes.add(close);
//            double tp = (high + low + close) / 3;
//            vwapNum += tp * volume; vwapDen += volume;
//        }
//
//        double latestPrice = optionData.getJSONArray(optionData.length() - 1).getDouble(4);
//        double vwap = vwapNum / vwapDen;
//        double rsi = indServices.calculateRSI(closes, 5);
//        double atr = indServices.calculateATR(optionData, 6);
//        double latestSar = indServices.calculateParabolicSAR(highs, lows, true).get(5);
//
//        return new OptionMetrics(closes, highs, lows, volumes, latestPrice, vwap, atr, rsi, latestSar, true);
//    }
//
//    
//    private int calculateQuantity(double price) {
//        int maxLots = (int) (capitalAmount / (price * lotSize));
//        return Math.min(maxLots * lotSize, indServices.getMaxQuantityForIndex(indexType));
//    }
//    
// // Helper class to store Greeks
//    private static class Greeks {
//        double iv, delta, gamma, theta, vega, vegaThetaRatio;
//
//        public Greeks(double iv, double delta, double gamma, double theta, double vega, double vegaThetaRatio) {
//            this.iv = iv;
//            this.delta = delta;
//            this.gamma = gamma;
//            this.theta = theta;
//            this.vega = vega;
//            this.vegaThetaRatio = vegaThetaRatio;
//        }
//    }
//
// // Method to fetch or calculate Greeks
//    private Greeks calculateGreeks(String optionType, double strikePrice, double atTheTime) {
//        double iv = 0, delta = 0, gamma = 0, theta = 0, vega = 0, vegaThetaRatio = 0;
//        boolean strikeFound = false;
//
//        if (!indexType.equalsIgnoreCase("SENSEX")) {
//            JSONObject payload = new JSONObject();
//            payload.put("name", indexType);
//            payload.put("expirydate", expiryDateWithMonthyear);
//
//            try {
//                JSONObject optionResponse = null;
//				try {
//					optionResponse = smartConnect.optionGreek(payload);
//				} catch (SmartAPIException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//                if (optionResponse != null && optionResponse.has("data")) {
//                    JSONArray dataArray = optionResponse.getJSONArray("data");
//                    for (int i = 0; i < dataArray.length(); i++) {
//                        JSONObject optionIvData = dataArray.getJSONObject(i);
//                        double strikeOptionPrice = Double.parseDouble(optionIvData.getString("strikePrice"));
//                        if (strikeOptionPrice == strikePrice) {
//                            iv = Double.parseDouble(optionIvData.getString("impliedVolatility"));
//                            delta = Double.parseDouble(optionIvData.getString("delta"));
//                            gamma = Double.parseDouble(optionIvData.getString("gamma"));
//                            theta = Double.parseDouble(optionIvData.getString("theta"));
//                            vega = Double.parseDouble(optionIvData.getString("vega"));
//                            vegaThetaRatio = Math.abs(vega / theta);
//                            strikeFound = true;
//                            break;
//                        }
//                    }
//                }
//            } catch (Exception e) {
//                logger.error("Error fetching Greeks from API: ", e);
//            }
//        }
//
//        // fallback calculation using Black-Scholes if SENSEX or strike not found
//        if (!strikeFound) {
//            double riskFreeRate = 0.065;
//            double T = indServices.getTimeToExpiryInYears(expiryDateStr);
//            boolean isCall = "CE".equalsIgnoreCase(optionType);
//            try {
//                iv = BlackScholesHelper.impliedVolatility(atTheTime, atTheTime, strikePrice, T, riskFreeRate, isCall);
//                delta = BlackScholesHelper.delta(atTheTime, strikePrice, riskFreeRate, iv, T, isCall);
//                gamma = BlackScholesHelper.gamma(atTheTime, strikePrice, riskFreeRate, iv, T);
//                theta = BlackScholesHelper.theta(atTheTime, strikePrice, riskFreeRate, iv, T, isCall) / (365 * 24 * 60);
//                vega = BlackScholesHelper.vega(atTheTime, strikePrice, riskFreeRate, iv, T);
//                vegaThetaRatio = Math.abs(vega / theta);
//            } catch (Exception e) {
//                logger.error("Error calculating Greeks using Black-Scholes: ", e);
//            }
//        }
//
//        return new Greeks(iv, delta, gamma, theta, vega, vegaThetaRatio);
//    }
//    
//    private void monitorOrder(String orderId, OptionMetrics metrics, double target, double stoploss, int quantity, Greeks greeks) {
//        String buyTimeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
//        reached.set(true);
//        positionTaken.set(true);
//
//        while (reached.get()) {
//            try {
//                double marketPrice = smartConnect.getLTP(exchangeOption, niftyString, optionToken).getDouble("ltp");
//
//                if (marketPrice >= target) {
////                    botHelper.profitHit(orderId, buyTimeStamp, niftyString, metrics.latestPrice, marketPrice,
////                            target, stoploss, quantity, metrics.latestPrice * quantity,
////                            metrics.vwap, indServices.calculateRSI(metrics.closes, 5),
////                            metrics.atr, metrics.latestSar, metrics.latestPrice,
////                            greeks.iv, greeks.delta, greeks.gamma, greeks.theta, greeks.vega, greeks.vegaThetaRatio);
//
//                    reached.set(false);
//                    positionTaken.set(false);
//                    cooldownMillis = 300_000L;
//                    break;
//
//                } else if (marketPrice <= stoploss) {
////                    botHelper.StopLossHit(orderId, buyTimeStamp, niftyString, metrics.latestPrice, marketPrice,
////                            target, stoploss, quantity, metrics.latestPrice * quantity,
////                            metrics.vwap, indServices.calculateRSI(metrics.closes, 5),
////                            metrics.atr, metrics.latestSar, metrics.latestPrice,
////                            greeks.iv, greeks.delta, greeks.gamma, greeks.theta, greeks.vega, greeks.vegaThetaRatio);
//
//                    reached.set(false);
//                    positionTaken.set(false);
//                    cooldownMillis = 300_000L;
//                    break;
//                }
//
////                indServices.logTradeToCSVForEffStrat(orderId, buyTimeStamp, niftyString, metrics.latestPrice,
////                        marketPrice, target, stoploss, "ORDER RUNNING", (double)quantity,
////                        metrics.latestPrice * quantity, "ORDER RUNNING",
////                        metrics.vwap, indServices.calculateRSI(metrics.closes, 5),
////                        metrics.atr, metrics.latestSar, metrics.latestPrice,
////                        greeks.iv, greeks.delta, greeks.gamma, greeks.theta, greeks.vega, greeks.vegaThetaRatio);
//
//                Thread.sleep(1000);
//
//            } catch (Exception e) {
//                logger.error("Error monitoring order: ", e);
//            }
//        }
//    }
//    
//    private JSONArray fetchNiftyValue(String token) {
//		return fetchCandleData(exchangeOption, token);
//	}
//
//	private JSONArray fetchNiftyTradeValue() throws InterruptedException {
//		int retries = 3;
//		while (retries-- > 0) {
//			JSONArray data = fetchCandleData(exchange, token);
//			if (data != null && data.length() > 0) return data;
//			Thread.sleep(2000);
//		}
//		return null;
//	}
//
//	private JSONArray fetchCandleData(String exchange, String token) {
//	    JSONObject request = new JSONObject();
//	    request.put("exchange", exchange);
//	    request.put("symboltoken", token);
//	    request.put("interval", "FIVE_MINUTE");
//
//	    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
//	    int minutesRounded = (now.getMinute() / 5) * 5;
//	    LocalDateTime roundedTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), minutesRounded));
//
//	    // Exclude the currently forming candle
//	    LocalDateTime toTime = roundedTime.minusMinutes(5);
//	    LocalDateTime fromTime = toTime.minusMinutes(35);
//
//	    // ✅ Use format without seconds
//	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
//
//	    request.put("fromdate", fromTime.format(formatter));
//	    request.put("todate", toTime.format(formatter));
//
//	    try {
//	        return smartConnect.candleData(request);
//	    } catch (Exception e) {
//	        logger.error("Error fetching candle data for token {}: {}", token, e.getMessage(), e);
//	        return null;
//	    }
//	}
//	
//    private static class OptionMetrics {
//        List<Double> closes, highs, lows, volumes;
//        double latestPrice, vwap, atr,rsi, latestSar;
//        boolean isValid;
//
//        public OptionMetrics(List<Double> closes, List<Double> highs, List<Double> lows, List<Double> volumes,
//                             double latestPrice, double vwap, double atr, double rsi, double latestSar, boolean isValid) {
//            this.closes = closes; this.highs = highs; this.lows = lows; this.volumes = volumes;
//            this.latestPrice = latestPrice; this.vwap = vwap; this.atr = atr; this.rsi = rsi; this.latestSar = latestSar;
//            this.isValid = isValid;
//        }
//    }
//    
//    @GetMapping("/api/bot/status")
//    @PreAuthorize("hasRole('USER')")
//    public BotStatus getBotStatus() {
//        return new BotStatus(positionTaken.get(), reached.get(), cooldownMillis);
//    }
//
//    // Inner class for status
//    public static class BotStatus {
//        private boolean positionTaken;
//        private boolean monitoring;
//        private long cooldownMillis;
//
//        public BotStatus(boolean positionTaken, boolean monitoring, long cooldownMillis) {
//            this.positionTaken = positionTaken;
//            this.monitoring = monitoring;
//            this.cooldownMillis = cooldownMillis;
//        }
//
//        // Getters for JSON serialization
//        public boolean isPositionTaken() { return positionTaken; }
//        public boolean isMonitoring() { return monitoring; }
//        public long getCooldownMillis() { return cooldownMillis; }
//    }
//
//    
//    /**
//     * Stops the bot gracefully.
//     */
//    public void stopBot() {
//        reached.set(false);       // stops monitoring
//        positionTaken.set(false); // resets position
//        logger.info("Trading bot stopped manually.");
//    }
//    
//    public void setIndexType(String indexType) {
//        this.indexType = indexType;
//    }
//
//    public void setCapitalAmount(double capitalAmount) {
//        this.capitalAmount = capitalAmount;
//    }
//
// // ✅ Set target and stoploss from UI
//    public void setTargetPercent(double targetPercent) {
//        this.targetPercent = targetPercent;
//    }
//
//    public void setStoplossPercent(double stoplossPercent) {
//        this.stoplossPercent = stoplossPercent;
//    }
//}
