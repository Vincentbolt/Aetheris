package com.aetheris.app.service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import com.aetheris.app.model.ApiSettings;
import com.aetheris.app.model.BotConfig;
import com.aetheris.app.model.Trade;
import com.aetheris.app.model.User;
import com.aetheris.app.repo.BotConfigRepository;
import com.aetheris.app.repo.SettingsRepository;
import com.aetheris.app.repo.TradeRepository;
import com.aetheris.app.repo.UserRepository;
import com.angelbroking.smartapi.SmartConnect;
import com.angelbroking.smartapi.http.exceptions.SmartAPIException;

@Service
public class TradingAetherBotService {
	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final Logger logger = LoggerFactory.getLogger(TradingAetherBotService.class);
	private IndicatorServices indServices;
	private AetherBotHelper botHelper;
	private String accessToken;
	private static final String TRANSACTION_TYPE_BUY = "BUY";
	private static final String ORDER_TYPE_LIMIT = "LIMIT";
	private static final String PRODUCT_MIS = "INTRADAY";
	private static final String VALIDITY_DAY = "DAY";
	private String exchangeOption;
	private String exchange;
	private static int lotSize = 0;
	private static String niftyString;
	private static String optionToken;
	private AtomicBoolean positionTaken = new AtomicBoolean(false);
	private AtomicBoolean reached = new AtomicBoolean(true);
	private AtomicBoolean stopBot = new AtomicBoolean(false);
	private ScheduledExecutorService scheduler;
	private String token = null;
	private boolean strategyStarted = false;
    private long lastExecutionTime = 0L;
    private long cooldownMillis = 1_000L;
    private long lastNiftyTradeFetchTime = 0;
    private long lastOptionDataFetchTime = 0;
    private JSONArray cachedNiftyTradeValue = null;
    private JSONArray cachedOptionData = null;
    private static int startHour = 9;
    private static int startMinutes = 46;
	private boolean checkVolatalityCriteria = false;
	private String indexType = null;
	private String expiryDateWithMonthyear = null;
	private double targetPercent = 0.0;
	private double stoplossPercent = 0.0;
	private double capitalAmount = 0.0;
	private String variety = null;
	private String totpKey = null;
	private String apiKey = null;
	private String clientId = null;
	private String password = null;
	
	
	@Autowired
    private BotConfigRepository botConfigRepository;
	
	@Autowired
    private SettingsRepository settingsRepository;
	
	@Autowired
    private UserRepository userRepository;
	
	@Autowired
    private TradeRepository tradeRepository;
	
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	
	private boolean isFromOrder = false;
	
	private static ZonedDateTime nextTriggerTime = null;
	
	@Value("${app.back-test:false}") // default to false
    private boolean backTestOnly;
	
	
	public SmartConnect createSmartConnect(String totpKey, String apiKey, String clientId, String password) {
	    SmartConnect smartConnect = new SmartConnect();
	    smartConnect.setApiKey(apiKey);
	    accessToken = smartConnect.generateSession(clientId, password, generateTotp(totpKey)).getAccessToken();
	    smartConnect.setAccessToken(accessToken);
	    return smartConnect;
	}

	
	public TradingAetherBotService(IndicatorServices indServices, AetherBotHelper botHelper) {
        this.indServices = indServices;
        this.botHelper = botHelper;
    }
	
	private String generateTotp(String totpKey) {
		return TOTPGenerator.generateTOTPCode(totpKey);
	}
	
	
	public synchronized boolean startBot(Long userId) {
		Optional<User> user = userRepository.findById(userId);
		User existingUser = user.get();
		Optional<BotConfig> configOpt = botConfigRepository.findByUser(existingUser);
		stopBot.set(false);

        BotConfig config = configOpt.get();
        indexType = config.getIndexType();
        expiryDateWithMonthyear = config.getExpiryDate();
        targetPercent = config.getTargetPercent();
        stoplossPercent = config.getStoplossPercent();
        capitalAmount = config.getCapitalAmount();
        variety = config.getVariety();
        SmartConnect smartConnect = null;
        
		try {
			ApiSettings settings = settingsRepository.findByUser(existingUser).orElse(null);
			totpKey = settings.getTotpKey();
			apiKey = settings.getApiKey();
			clientId = settings.getClientId();
			password = settings.getPassword();
			
			smartConnect = createSmartConnect(totpKey, apiKey, clientId, password);
			
			indServices = new IndicatorServices();
			botHelper = new AetherBotHelper();
			
			token = botHelper.getNSETokens(indexType);
			exchangeOption = botHelper.getExchangeOption(indexType);
			exchange = botHelper.getExchange(indexType);
			lotSize = botHelper.getLotSize(indexType);
			
			System.out.println("Connection Initialized.");
		} catch (Exception e) {
			System.out.println("Error in initializeConnection ");
			return false;
		}
		
		System.setProperty("https.protocols", "TLSv1.2");
		this.startStrategy(smartConnect, existingUser);
		return true;
	}
	
	public void startStrategy(SmartConnect smartConnect, User userId) {
	    System.out.println("Starting Strategy");

	    ZoneId istZone = ZoneId.of("Asia/Kolkata");
	    nextTriggerTime = getNextTriggerTime(ZonedDateTime.now(istZone));

	    // ✅ Guard against multiple starts
	    if (scheduler != null && !scheduler.isShutdown()) {
	        System.out.println("⚠️ Bot is already running. Ignoring new start request.");
	        return;
	    }

	    // ✅ Reset old scheduler if needed
	    if (scheduler != null && scheduler.isShutdown()) {
	        System.out.println("Cleaning up old scheduler...");
	        scheduler = null;
	    }

	    stopBot.set(false);
	    strategyStarted = false;

	    // ✅ Create new scheduler
	    scheduler = Executors.newSingleThreadScheduledExecutor();
	    System.out.println("✅ Scheduler created.");

	    scheduler.scheduleAtFixedRate(() -> {
	        try {
	            if (stopBot.get()) {
	                System.out.println("🛑 Stop signal received. Cancelling strategy...");
	                scheduler.shutdownNow();
	                scheduler = null;
	                return;
	            }

	            // ✅ Current IST time
	            ZonedDateTime now = ZonedDateTime.now(istZone);
	            if (isFromOrder) {
	            	nextTriggerTime = getNextTriggerTime(ZonedDateTime.now(istZone));
	            	startHour = now.getHour();
	            	startMinutes = now.getMinute();
	            }
	            ZonedDateTime startTime = now.withHour(startHour).withMinute(startMinutes).withSecond(0).withNano(0);

	            // ✅ If strategy not started yet, wait until startTime
	            if (!strategyStarted && now.isBefore(startTime)) {
	                return;
	            }

	            // ✅ Start exactly at startTime or nextTriggerTime
	            if (!strategyStarted && (now.equals(startTime) || now.isAfter(startTime))) {
	                if (now.equals(startTime) || (now.getHour() == nextTriggerTime.getHour() && now.getMinute() == nextTriggerTime.getMinute())) {
	                    executeStrategy(smartConnect, userId);
	                    strategyStarted = true;
	                    lastExecutionTime = System.currentTimeMillis();
	                }
	            }

	            // ✅ Continue executing every 5 minutes while market is open
	            if (strategyStarted && isMarketOpen()) {
	                long currentTime = System.currentTimeMillis();
	                if (currentTime - lastExecutionTime >= cooldownMillis) {
	                    executeStrategy(smartConnect, userId);
	                    lastExecutionTime = currentTime;
	                }
	            }

	        } catch (Exception e) {
	            if (e instanceof InterruptedException) {
	                System.out.println("Strategy interrupted. Stopping...");
	                Thread.currentThread().interrupt();
	            } else {
	                e.printStackTrace();
	            }
	        }
	    }, 0, 1, TimeUnit.SECONDS);
	}

	/**
	 * ✅ Market open between 09:46 and 15:30
	 */
	private boolean isMarketOpen() {
		LocalTime now = istTimeNow();
	    return now.isAfter(LocalTime.of(startHour, startMinutes).minusSeconds(1))
	            && now.isBefore(LocalTime.of(15, 30));
	}

	/**
	 * Calculates the next trigger time:
	 * → the next 5-min multiple after current time, then +1 minute.
	 * Examples:
	 *  - 10:49 → 10:51
	 *  - 10:55 → 10:56
	 *  - 11:03 → 11:06
	 */
	private ZonedDateTime getNextTriggerTime(ZonedDateTime now) {
	    int intervalMinutes = 5; // replace with your interval if different
	    int currentMinute = now.getMinute();

	    // Calculate previous multiple and next multiple of the interval
	    int baseMultiple = (currentMinute / intervalMinutes) * intervalMinutes;
	    int nextMultiple = baseMultiple + intervalMinutes;

	    int hour = now.getHour();
	    if (nextMultiple >= 60) {
	        nextMultiple -= 60;
	        hour = (hour + 1) % 24;
	    }

	    // Construct next multiple time in IST
	    ZonedDateTime nextMultipleTime = now.withHour(hour).withMinute(nextMultiple).withSecond(0).withNano(0);

	    // Add +1 minute offset for trigger logic
	    ZonedDateTime triggerTime = nextMultipleTime.plusMinutes(1);

	    // If trigger time is not after now, move to the next interval
	    if (!triggerTime.isAfter(now)) {
	        triggerTime = triggerTime.plusMinutes(intervalMinutes);
	    }

	    return triggerTime;
	}

	
	/**
     * Stops the bot gracefully.
     */
	public synchronized void stopBot() {
	    stopBot.set(true);
	    reached.set(false);
	    positionTaken.set(false);

	    if (scheduler != null && !scheduler.isShutdown()) {
	        scheduler.shutdownNow();
	        scheduler = null; // ✅ Clear the reference
	        System.out.println("✅ Strategy scheduler stopped successfully.");
	    } else {
	        System.out.println("⚠️ No active scheduler to stop.");
	    }
	}
    
	private void executeStrategy(SmartConnect smartConnect, User userId) {
		try {
			isFromOrder = false;
			System.out.println("Execute Strategy: " + "Running");
			long nowIstMillis = Instant.now().atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();

			JSONArray firstCandle = null;
			JSONArray secondCandle = null;
			
			// 👇 Throttle fetchNiftyTradeValue() to once every 5 minutes
			if (nowIstMillis - lastNiftyTradeFetchTime > 5 * 60 * 1000 || cachedNiftyTradeValue == null) {
				cachedNiftyTradeValue = fetchNiftyTradeValue(smartConnect);
				if (cachedNiftyTradeValue == null || cachedNiftyTradeValue.length() < 7) {
					if (cachedNiftyTradeValue != null) {
						System.out.println("Not enough candles. Candles Formed: {}" + cachedNiftyTradeValue.length());
					} else {
						System.out.println("Not enough candles.");
					}
					cooldownMillis = 1_000L;
					return; 
				}
				lastNiftyTradeFetchTime = nowIstMillis;
				firstCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 2);
				secondCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 1);
			}
			JSONArray response1 = cachedNiftyTradeValue;
			firstCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 2);
			secondCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 1);


			if (response1 == null || response1.length() < 7) {
				if (response1 != null) {
					System.out.println("Not enough candles. Candles Formed: " + response1.length());
				} else {
					System.out.println("Not enough candles.");
				}
				cooldownMillis = 1_000L;
				return; 
			}
			// Extract last 6 candles
			List<Double> closes = new ArrayList<>();

			double firstOpen= firstCandle.getDouble(1);
			double firstHigh = firstCandle.getDouble(2);
			double firstLow = firstCandle.getDouble(3);
			double firstClose = firstCandle.getDouble(4);
			double secondOpen  = secondCandle.getDouble(1);
			double secondHigh  = secondCandle.getDouble(2);
			double secondLow   = secondCandle.getDouble(3);
			double secondClose = secondCandle.getDouble(4);
			
			// Create arrays to hold the two candle data
	        double[] opens  = { firstOpen, secondOpen };
	        double[] highs  = { firstHigh, secondHigh };
	        double[] lows   = { firstLow, secondLow };
	        double[] fCloses = { firstClose, secondClose };

			String optionType = null;
			double avg = 0.0;
			double diff = 0.0;
			if (!positionTaken.get()) {
				if (secondHigh > firstHigh && secondLow > firstLow) {
					String trend = botHelper.detectTrend(opens, highs, lows, fCloses);
					if (trend.equalsIgnoreCase("Bullish")) {
						optionType = "CE";
						diff = secondHigh - firstLow;
						avg  = diff / 2;
					}
				} else if (secondLow < firstLow && secondHigh < firstHigh) {
					String trend = botHelper.detectTrend(opens, highs, lows, fCloses);
					if (trend.equalsIgnoreCase("Bearish")) {
						optionType = "PE";
						diff = firstHigh - secondLow;
						avg  = diff / 2;
					}
				}
				
				boolean confirmTrend = false;

				if (optionType != null && (optionType.equalsIgnoreCase("CE") || optionType.equalsIgnoreCase("PE"))) {

				    double threshold;
				    // Set threshold based on index type
				    if (indexType.equalsIgnoreCase("NIFTY") || indexType.equalsIgnoreCase("FINNIFTY")) {
				        threshold = 3.0;
				    } else if (indexType.equalsIgnoreCase("SENSEX") || indexType.equalsIgnoreCase("BANKNIFTY")) {
				        threshold = 10.0;
				    } else {
				        threshold = 1.5;
				    }

				    // Check trend
				    if (optionType.equalsIgnoreCase("CE")) {
				        if (secondClose >= secondOpen + threshold) {
				            confirmTrend = true;
				        } else {
				            optionType = null;
				        }
				    } else { // PE
				        if (secondOpen >= secondClose + threshold) {
				            confirmTrend = true;
				        } else {
				            optionType = null;
				        }
				    }
				}
				
				if(optionType == null && !confirmTrend) {
					logger.info("Index Basic Trend pattern not formed.");
					isFromOrder = true;
					cooldownMillis = 300_000L;
					return;
				}

				targetPercent = calculateTargetPercent(indexType, avg);
				
				if (optionType != null) {
					// ✅ RSI Filter
					JSONArray currentResponse = fetchMarketCurrentValue(smartConnect);
					
					if (currentResponse == null || currentResponse.length() < 7) {
						if (currentResponse != null) {
							System.out.println("Not enough candles. Candles Formed: {}" +  currentResponse.length());
						} else {
							System.out.println("Not enough candles.");
						}
						cooldownMillis = 1_000L;
						return; 
					}
					
					for (int i = currentResponse.length() - 6; i < currentResponse.length(); i++) {
						JSONArray candle = currentResponse.getJSONArray(i);
						double close = candle.getDouble(4);
						closes.add(close);
					}
					
					firstCandle = currentResponse.getJSONArray(currentResponse.length() - 2);
					System.out.println("firstCandle : " + firstCandle);
					secondCandle = currentResponse.getJSONArray(currentResponse.length() - 1);
					System.out.println("secondCandle : " + secondCandle);
					
					double rsi = indServices.calculateRSI(closes, 5);

					if (optionType.equals("CE")) {
					    // allow wider RSI in trending bullish markets
					    if (rsi < 45 || rsi > 95) {
					        logger.info("CE RSI filter failed: {}", rsi);
					        cooldownMillis = 1_000L;
					        return;
					    }
					} else if (optionType.equals("PE")) {
					    // allow wider RSI in trending bearish markets
					    if (rsi > 55 || rsi < 3) {
					        logger.info("PE RSI filter failed: {}", rsi);
					        cooldownMillis = 1_000L;
					        return;
					    }
					}

					// 🧠 Continue with your existing code to generate symbol,
					// fetch token, check market price, place order, etc.
					JSONObject ltpData = smartConnect.getLTP(exchange, exchangeOption, token);
					double atTheTime = ltpData.getDouble("ltp");
					int roundTo = botHelper.getRoundedValue(indexType);

					double strikePrice = Math.round(atTheTime / roundTo) * roundTo;
					String strikeOption = ((int) strikePrice) + optionType;
					niftyString = indServices.generateNiftyString(indexType, strikeOption, expiryDateWithMonthyear);
					optionToken = indServices.getToken(niftyString);
					if (optionToken != null && !stopBot.get()) {
						// 👇 Throttle fetchNiftyValue(optionToken) to once every 5 minutes
						 JSONArray optionFirstCandle = null;
						 JSONArray optionSecondCandle = null;
						if (nowIstMillis - lastOptionDataFetchTime > 5 * 60 * 1000 || cachedOptionData == null) {
						    cachedOptionData = fetchNiftyValue(optionToken, smartConnect);
						    lastOptionDataFetchTime = nowIstMillis;
						    optionFirstCandle = cachedOptionData.getJSONArray(cachedOptionData.length() - 2);
							optionSecondCandle = cachedOptionData.getJSONArray(cachedOptionData.length() - 1);
						}
						JSONArray optionData = cachedOptionData;
						optionFirstCandle = cachedOptionData.getJSONArray(cachedOptionData.length() - 2);
						optionSecondCandle = cachedOptionData.getJSONArray(cachedOptionData.length() - 1);
						
						if (optionData != null && optionData.length() >= 2) {
							// Extract last 6 candles
							List<Double> closesofOption = new ArrayList<>();
							List<Double> optionHighs = new ArrayList<>();
							List<Double> optionLows = new ArrayList<>();
							List<Double> optionVolumes = new ArrayList<>();

							double vwapOptionNumerator = 0, vwapOptionDenominator = 0;

							double optionFirstHigh = optionFirstCandle.getDouble(2);
							double optionFirstLow = optionFirstCandle.getDouble(3);
							double optionSecondHigh = optionSecondCandle.getDouble(2);
							double optionSecondLow = optionSecondCandle.getDouble(3);
							double optionSecondClose = optionSecondCandle.getDouble(4);

							JSONArray optionCurrentData = fetchCurrentOptionValue(optionToken, smartConnect);
							
							if (optionCurrentData == null || optionCurrentData.length() < 7) {
								if (optionCurrentData != null) {
									System.out.println("Not enough candles. Candles Formed: " + optionCurrentData.length());
								} else {
									System.out.println("Not enough candles.");
								}
								cooldownMillis = 1_000L;
								return; 
							}
							
							for (int i = optionCurrentData.length() - 6; i < optionCurrentData.length(); i++) {
								JSONArray candle = optionCurrentData.getJSONArray(i);
								double high = candle.getDouble(2);
								double low = candle.getDouble(3);
								double close = candle.getDouble(4);
								double volume = candle.getDouble(5);
								closesofOption.add(close);
								optionHighs.add(high);
								optionLows.add(low);
								optionVolumes.add(volume);

								// ✅ VWAP calculation
								double typicalPrice = (high + low + close) / 3;
								vwapOptionNumerator += typicalPrice * volume;
								vwapOptionDenominator += volume;
							}

							double vwapOption = vwapOptionNumerator / vwapOptionDenominator;
								
							if (optionSecondHigh > optionFirstHigh && optionSecondLow > optionFirstLow && optionSecondHigh != optionSecondClose) {
								if (optionSecondClose <= vwapOption) {
									System.out.println("VWAP option Filter Failed: {}, optionSecondClose: " + vwapOption + optionSecondClose);
									cooldownMillis = 1_000L;
									return;
								}
							} else {
								System.out.println("Option Basic Trend pattern not formed.");
								cooldownMillis = 1_000L;
								return;
							}

							// ✅ Option RSI Filter
							double rsiOption = indServices.calculateRSI(closesofOption, 5);

							if (!botHelper.isOptionRSIFilterPassed(rsiOption, rsi)) {
								cooldownMillis = 1_000L;
								return;
							}

							JSONObject ltpOptionData = smartConnect.getLTP(exchangeOption, niftyString, optionToken);
							double atTheTimeOption = ltpOptionData.getDouble("ltp");

							JSONObject forGettingAskAndBidData = botHelper.forFetchAskAndBidPrice(optionToken, smartConnect, exchangeOption);

							if (!botHelper.isLiquidityValid(forGettingAskAndBidData, atTheTimeOption)) {
								cooldownMillis = 1_000L;
								return;
							}

							int optionSTrikeRoundTo = botHelper.getRoundedValue(indexType);		
							double optionSTrikePrice = Math.round(atTheTime / optionSTrikeRoundTo) * optionSTrikeRoundTo;
							double iv = 0.0, delta = 0.0, gamma = 0.0, theta = 0.0, vega = 0.0;
							double vegaThetaRatio = 0.0;
							boolean strikeFound = false;

							if (!indexType.equalsIgnoreCase("SENSEX")) {
								JSONObject payload = new JSONObject();
								payload.put("name", indexType);
								payload.put("expirydate", expiryDateWithMonthyear);
								JSONObject optionResponse = null;
								try {
									optionResponse = smartConnect.optionGreek(payload);
									System.out.println("optionGreek: {}" + optionResponse);
								} catch (IOException | SmartAPIException e) {
									e.printStackTrace();
								}

								if (optionResponse == null || !optionResponse.has("data")) {
									logger.warn("optionResponse is null or missing 'data' field.");
									cooldownMillis = 1_000L;
									return;
								}

								JSONArray dataArray = optionResponse.getJSONArray("data");


								for (int i = 0; i < dataArray.length(); i++) {
									JSONObject optionIvData = dataArray.getJSONObject(i);
									double strikeOptionPrice = Double.parseDouble(optionIvData.getString("strikePrice"));

									if (strikeOptionPrice == optionSTrikePrice) {
										iv = Double.parseDouble(optionIvData.getString("impliedVolatility"));
										delta = Double.parseDouble(optionIvData.getString("delta"));
										gamma = Double.parseDouble(optionIvData.getString("gamma"));
										theta = Double.parseDouble(optionIvData.getString("theta"));
										vega = Double.parseDouble(optionIvData.getString("vega"));
										vegaThetaRatio = Math.abs(vega / theta);
										strikeFound = true;
										break; // exit loop once found
									}
								}
							}

							if (strikeFound && !indexType.equalsIgnoreCase("SENSEX")) {
								System.out.println("Strike {} - IV: {}, Delta: {}, Gamma: {}, Theta: {}, Vega: {}" + optionSTrikePrice + iv + delta + gamma + theta + vega);
								if (checkVolatalityCriteria) {
									if (!botHelper.isIVValid(iv)) {
										cooldownMillis = 1_000L;
										return;
									}
									if (!botHelper.isVegaThetaValid(vegaThetaRatio, indexType)) {
										cooldownMillis = 1_000L;
										return;
									}
								}
							} else {
								logger.warn("Strike price {} not found in the response.", optionSTrikePrice);
							}

							if (atTheTimeOption < 80) {
								targetPercent = 1.0;
							}
							
							double targetValue = 0.0, stoplossValue = 0.0;
							targetValue = Math.round(atTheTimeOption * (targetPercent / 100) * 100.0) / 100.0;
							stoplossValue = Math.round(atTheTimeOption * (stoplossPercent / 100) * 100.0) / 100.0;

							targetValue = targetValue + atTheTimeOption;
							stoplossValue = atTheTimeOption - stoplossValue;

							//  Risk Reward Check
							if (targetPercent == 2.00) {
								if (!botHelper.isRiskRewardFavorable(atTheTimeOption, targetValue, stoplossValue)) {
									cooldownMillis = 1_000L;
									return;
								}
							}


							int maxLots = (int) (capitalAmount / (atTheTimeOption * lotSize));
							int quantity = Math.min(maxLots * lotSize, indServices.getMaxQuantityForIndex(indexType));
							double capitalUsed = atTheTimeOption * quantity;

							String orderId = botHelper.placeOrder(niftyString, optionToken, TRANSACTION_TYPE_BUY, atTheTimeOption, quantity, targetValue, stoplossValue,smartConnect,variety, exchangeOption, ORDER_TYPE_LIMIT,PRODUCT_MIS, VALIDITY_DAY);

							//TradingBotUtil.sendTelegramMessage("🚀 New Entry: " + niftyString + "\nPrice: ₹" + atTheTimeOption + "\nTarget Price: ₹" + targetValue + "\nStoploss Price: ₹" + stoplossValue + "\n From AetherBot");
							Long tradeId = this.saveTradeEntry(niftyString, capitalUsed, userId);
							reached.set(true);
							positionTaken.set(true);
							
							if (backTestOnly) {
								while (reached.get()) {
									JSONObject optionDataCheck = smartConnect.getLTP(exchangeOption, niftyString, optionToken);
									if (optionDataCheck == null || optionDataCheck.length() == 0) {
										logger.warn("Failed to fetch candle data. Retrying...");
										try {
											Thread.sleep(3000);
										} catch (InterruptedException e) {
											e.printStackTrace();
										}
										continue;
									}

									double marketPr = optionDataCheck.getDouble("ltp");
									if (marketPr >= targetValue) {
										capitalUsed = marketPr * quantity;
										this.updateTradeExit(tradeId, capitalUsed, istNow());
										positionTaken.set(false);
										reached.set(false);
										cooldownMillis = 300_000L;
										isFromOrder = true;
										break;
									} else if (marketPr <= stoplossValue) {
										capitalUsed = marketPr * quantity;
										this.updateTradeExit(tradeId, capitalUsed, istNow());
										positionTaken.set(false);
										reached.set(false);
										cooldownMillis = 300_000L;
										isFromOrder = true;
										break;
									}
									try {
										Thread.sleep(1000);
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
							} else {
								String uniqueOrderid = null;
								int maxRetries = 3;
								int attempt = 0;

								while (attempt < maxRetries) {
								    try {
								        JSONObject jsonObject = smartConnect.getOrderHistory(clientId);

								        if (jsonObject != null && jsonObject.has("data")) {
								            JSONArray dataArray = jsonObject.getJSONArray("data");

								            for (int i = 0; i < dataArray.length(); i++) {
								                JSONObject order = dataArray.getJSONObject(i);
								                if (order.getString("orderid").equals(orderId)) {
								                    uniqueOrderid = order.getString("uniqueorderid");
								                    break;
								                }
								            }

								            // Break retry loop if order found
								            if (uniqueOrderid != null) {
								                break;
								            }
								        } else {
								            System.out.println("No data field in response or null response.");
								        }

								    } catch (Exception e) {
								        System.out.println("Attempt " + (attempt + 1) + " failed: " + e.getMessage());
								        // Optionally: e.printStackTrace();
								    }

								    attempt++;
								    
								    // Optional: wait before retrying (e.g., 1 second)
								    try {
								        Thread.sleep(1000); 
								    } catch (InterruptedException ie) {
								        Thread.currentThread().interrupt();
								        break;
								    }
								}

								if (uniqueOrderid != null) {
									JSONObject individualOrderObject = null;
									String orderStatus = null;

									maxRetries = 3;
									attempt = 0;

									while (attempt < maxRetries) {
									    try {
									        individualOrderObject = smartConnect.getIndividualOrderDetails(uniqueOrderid);

									        if (individualOrderObject != null && individualOrderObject.has("data")) {
									            JSONObject individualOrderData = individualOrderObject.getJSONObject("data");
									            orderStatus = individualOrderData.getString("orderstatus");
									            break; // success, exit loop
									        } else {
									            System.out.println("Invalid or null response received on attempt " + (attempt + 1));
									        }
									    } catch (SmartAPIException e) {
									        System.out.println("SmartAPIException on attempt " + (attempt + 1) + ": " + e.getMessage());
									        // Optionally: e.printStackTrace();
									    } catch (Exception e) {
									        System.out.println("Unexpected error on attempt " + (attempt + 1) + ": " + e.getMessage());
									    }

									    attempt++;

									    // Optional delay before retrying
									    try {
									        Thread.sleep(1000);
									    } catch (InterruptedException ie) {
									        Thread.currentThread().interrupt(); // restore interrupt status
									        break;
									    }
									}

									if (orderStatus != null && orderStatus.equalsIgnoreCase("pending")) {
									    int maxPollingAttempts = 10; // Set a limit to avoid infinite loop
									    int pollingAttempt = 0;

									    while (orderStatus.equalsIgnoreCase("pending") && pollingAttempt < maxPollingAttempts) {
									        try {
									            // Wait between polling attempts (e.g., 2 seconds)
									            Thread.sleep(2000);

									            // Fetch updated order details
									            individualOrderObject = smartConnect.getIndividualOrderDetails(uniqueOrderid);
									            
									            if (individualOrderObject != null && individualOrderObject.has("data")) {
									                JSONObject individualOrderData = individualOrderObject.getJSONObject("data");
									                orderStatus = individualOrderData.getString("orderstatus");

									                if (orderStatus.equalsIgnoreCase("executed")) {
									                    System.out.println("Order Executed.");
									                    break;
									                }
									            } else {
									                System.out.println("Invalid response received during polling.");
									            }

									        } catch (SmartAPIException e) {
									            System.out.println("SmartAPIException during polling: " + e.getMessage());
									        } catch (InterruptedException ie) {
									            Thread.currentThread().interrupt();
									            System.out.println("Polling interrupted.");
									            break;
									        } catch (Exception ex) {
									            System.out.println("Unexpected exception during polling: " + ex.getMessage());
									        }

									        pollingAttempt++;
									    }

									    if (!orderStatus.equalsIgnoreCase("executed")) {
									        System.out.println("Order did not execute within polling limit.");
									    }
									}


									while (reached.get()) {
										JSONObject optionDataCheck = smartConnect.getLTP(exchangeOption, niftyString, optionToken);
										if (optionDataCheck == null || optionDataCheck.length() == 0) {
											logger.warn("Failed to fetch candle data. Retrying...");
											try {
												Thread.sleep(3000);
											} catch (InterruptedException e) {
												e.printStackTrace();
											}
											continue;
										}

										double marketPr = optionDataCheck.getDouble("ltp");
										if (marketPr >= targetValue) {
											capitalUsed = marketPr * quantity;
											this.updateTradeExit(tradeId, capitalUsed, istNow());
											positionTaken.set(false);
											reached.set(false);
											cooldownMillis = 300_000L;
											isFromOrder = true;
											break;
										} else if (marketPr <= stoplossValue) {
											capitalUsed = marketPr * quantity;
											this.updateTradeExit(tradeId, capitalUsed, istNow());
											positionTaken.set(false);
											reached.set(false);
											cooldownMillis = 300_000L;
											isFromOrder = true;
											break;
										}
										try {
											Thread.sleep(2000);
										} catch (InterruptedException e) {
											e.printStackTrace();
										}
									}
								}
							}
						}
					} else {
						logger.error("Token is not generated");
					}
				}
			}

		} catch (Exception e) {
			logger.error("Error in executeStrategy: ", e);
		}
	}
	
	public Long saveTradeEntry(String niftyString, double entryPrice, User userId) {
        Trade trade = new Trade();
        trade.setUser(userId);
        trade.setIndexOptionName(niftyString);
        trade.setEntryPrice(entryPrice);
        trade.setEntryTime(istNow());

        Trade saved = tradeRepository.save(trade);
        return saved.getId(); // Save this ID to update later
    }
	
	public void updateTradeExit(Long tradeId, double exitPrice, LocalDateTime exitTime) {
        tradeRepository.findById(tradeId).ifPresent(trade -> {
            trade.setExitPrice(exitPrice);
            trade.setExitTime(exitTime);

            // calculate profit or loss
            double plPercent = ((exitPrice - trade.getEntryPrice()) / trade.getEntryPrice()) * 100;
            trade.setProfitOrLossPercent(plPercent);

            tradeRepository.save(trade); // update
        });
    }
	
	private JSONArray fetchMarketCurrentValue(SmartConnect smartConnect) throws InterruptedException {
		int retries = 3;
		while (retries-- > 0) {
			JSONArray data = fetchCurrentCandleData(exchange, token, smartConnect);
			if (data != null && data.length() > 0) return data;
			Thread.sleep(2000);
		}
		return null;
	}
	
	private JSONArray fetchCurrentCandleData(String exchange, String token, SmartConnect smartConnect) {
		JSONObject request = new JSONObject();
		request.put("exchange", exchange);
		request.put("symboltoken", token);
		request.put("interval", "FIVE_MINUTE");

		LocalDateTime now = istNow().truncatedTo(ChronoUnit.MINUTES);
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
	
	private JSONArray fetchNiftyValue(String token, SmartConnect smartConnect) {
		return fetchCandleData(exchangeOption, token, smartConnect);
	}
	
	private JSONArray fetchCurrentOptionValue(String optionToken, SmartConnect smartConnect) {
		JSONObject request = new JSONObject();
		request.put("exchange", exchangeOption);
		request.put("symboltoken", optionToken);
		request.put("interval", "FIVE_MINUTE");

		LocalDateTime now = istNow().truncatedTo(ChronoUnit.MINUTES);
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

	private JSONArray fetchNiftyTradeValue(SmartConnect smartConnect) throws InterruptedException {
		int retries = 3;
		while (retries-- > 0) {
			JSONArray data = fetchCandleData(exchange, token, smartConnect);
			if (data != null && data.length() > 0) return data;
			Thread.sleep(2000);
		}
		return null;
	}

	private JSONArray fetchCandleData(String exchange, String token, SmartConnect smartConnect) {
	    JSONObject request = new JSONObject();
	    request.put("exchange", exchange);
	    request.put("symboltoken", token);
	    request.put("interval", "FIVE_MINUTE");

	    LocalDateTime now = istNow().truncatedTo(ChronoUnit.MINUTES);
	    int minutesRounded = (now.getMinute() / 5) * 5;
	    LocalDateTime roundedTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), minutesRounded));

	    // Exclude the currently forming candle
	    LocalDateTime toTime = roundedTime.minusMinutes(5);
	    LocalDateTime fromTime = toTime.minusMinutes(35);

	    // ✅ Use format without seconds
	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	    request.put("fromdate", fromTime.format(formatter));
	    request.put("todate", toTime.format(formatter));

	    try {
	        return smartConnect.candleData(request);
	    } catch (Exception e) {
	        logger.error("Error fetching candle data for token {}: {}", token, e.getMessage(), e);
	        return null;
	    }
	}

    
    @GetMapping("/api/bot/status")
    @PreAuthorize("hasRole('USER')")
    public BotStatus getBotStatus() {
        return new BotStatus(positionTaken.get(), reached.get(), cooldownMillis);
    }

    // Inner class for status
    public static class BotStatus {
        private boolean positionTaken;
        private boolean monitoring;
        private long cooldownMillis;

        public BotStatus(boolean positionTaken, boolean monitoring, long cooldownMillis) {
            this.positionTaken = positionTaken;
            this.monitoring = monitoring;
            this.cooldownMillis = cooldownMillis;
        }

        // Getters for JSON serialization
        public boolean isPositionTaken() { return positionTaken; }
        public boolean isMonitoring() { return monitoring; }
        public long getCooldownMillis() { return cooldownMillis; }
    }
    
    public static LocalDateTime istNow() {
        return LocalDateTime.now(IST);
    }

    public static LocalTime istTimeNow() {
        return LocalTime.now(IST);
    }

    public static LocalDate istDateNow() {
        return LocalDate.now(IST);
    }
    
    private double calculateTargetPercent(String indexType, double avg) {
        if (indexType == null) {
            return 1; // Default safe value
        }

        switch (indexType.toUpperCase()) {
            case "NIFTY":
            case "FINNIFTY":
                return (avg >= 15.0) ? 2.0 : 1.0;

            case "BANKNIFTY":
            case "SENSEX":
                return (avg >= 30.0) ? 2.0 : 1.0;

            default:
                return (avg >= 10.0) ? 2.0 : 1.0;
        }
    }
    
}
