package com.aetheris.app.service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	private Timer strategyTimer;
	private String token = null;
	private boolean strategyStarted = false;
    private long lastExecutionTime = 0L;
    private long cooldownMillis = 1_000L;
    private long lastNiftyTradeFetchTime = 0;
    private long lastOptionDataFetchTime = 0;
    private JSONArray cachedNiftyTradeValue = null;
    private JSONArray cachedOptionData = null;
    private static final int startHour = 9;
    private static final int startMinutes = 46;
    private static final int intervalMinutes = 5;
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
	
	
	public void startBot(Long userId) {
		TradingAetherBotService app = new TradingAetherBotService(indServices, botHelper);
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
		}
		
		System.setProperty("https.protocols", "TLSv1.2");
		app.startStrategy(smartConnect, existingUser);
	}
	
	public void startStrategy(SmartConnect smartConnect, User userId) {
		System.out.println("Starting Strategy");
		stopBot.set(false);
		strategyTimer = new Timer();
	    strategyTimer.scheduleAtFixedRate(new TimerTask() {
	        @Override
	        public void run() {
	        	// ✅ Stop condition check
	            if (stopBot.get()) {
	            	System.out.println("Bot stop signal received. Cancelling strategy timer...");
	                strategyTimer.cancel();
	                strategyTimer.purge();
	                return;
	            }
	            
	        	LocalTime now = istTimeNow();
	            LocalTime startTime = LocalTime.of(startHour, startMinutes);
	            // ✅ If before 9:46, wait until 9:46 to start
	            if (!strategyStarted && now.isBefore(startTime)) {
	            	System.out.println("Strategy not started yet. Now: " + now + ", StartTime: " + startTime);
	                return;
	            }

	            // ✅ Start exactly at 9:46 or the next 5-min multiple after current time
	            if (!strategyStarted && (now.equals(startTime) || now.isAfter(startTime))) {
	                LocalTime nextTriggerTime = getNextTriggerTime(now);
	                if (now.equals(startTime)) {
	                    executeStrategy(smartConnect, userId);
	                    strategyStarted = true;
	                    lastExecutionTime = System.currentTimeMillis();
	                } else if (now.equals(nextTriggerTime)) {
	                    executeStrategy(smartConnect, userId);
	                    strategyStarted = true;
	                    lastExecutionTime = System.currentTimeMillis();
	                } else {
	                	System.out.println("Strategy not started yet. Now: " + "Failed in now.equals(startTime) & now.equals(nextTriggerTime)" + now + startTime + nextTriggerTime + strategyStarted);
		            }
	            } else {
	            	System.out.println("Strategy not started yet " + "Failed in !strategyStarted && (now.equals(startTime) || now.isAfter(startTime))");
	            }

	            // ✅ Continue executing every 5 minutes while market is open
	            if (strategyStarted && isMarketOpen()) {
	                long currentTime = System.currentTimeMillis();
	                if (currentTime - lastExecutionTime >= cooldownMillis) {
	                    executeStrategy(smartConnect, userId);
	                    lastExecutionTime = currentTime;
	                }
	            } else {
	            	System.out.println("Strategy not started yet " + "Market is Closed");
	            }
	        }
	    }, 0, 1000); // check every 1 second
	}

	/**
	 * ✅ Market open between 09:46 and 15:30
	 */
	private boolean isMarketOpen() {
		LocalTime now = istTimeNow();
	    return now.isAfter(LocalTime.of(startHour, startMinutes).minusSeconds(1))
	            && now.isBefore(LocalTime.of(17, 30));
	}

	/**
	 * ✅ Get next 5-minute interval after current time
	 * Example: 10:03 → 10:06, 10:37 → 10:41, 11:04 → 11:06
	 */
	private LocalTime getNextTriggerTime(LocalTime now) {
        int minute = now.getMinute();
        int nextMultiple = ((minute / intervalMinutes) + 1) * intervalMinutes;
        if (nextMultiple >= 60) {
            return LocalTime.of(now.getHour() + 1, nextMultiple - 60, 0);
        } else {
            return LocalTime.of(now.getHour(), nextMultiple, 0);
        }
    }
	
	private void executeStrategy(SmartConnect smartConnect, User userId) {
		try {
			System.out.println("Execute Strategy: " + "Running");
			long nowIstMillis = Instant.now().atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli();

			JSONArray firstCandle = null;
			JSONArray secondCandle = null;
			
			// 👇 Throttle fetchNiftyTradeValue() to once every 5 minutes
			if (nowIstMillis - lastNiftyTradeFetchTime > 5 * 60 * 1000 || cachedNiftyTradeValue == null) {
				cachedNiftyTradeValue = fetchNiftyTradeValue(smartConnect);
				if (cachedNiftyTradeValue == null || cachedNiftyTradeValue.length() < 7) {
					if (cachedNiftyTradeValue != null) {
						logger.info("Not enough candles. Candles Formed: {}", cachedNiftyTradeValue.length());
					} else {
						logger.info("Not enough candles.");
					}
					cooldownMillis = 1_000L;
					return; 
				}
				lastNiftyTradeFetchTime = nowIstMillis;
				firstCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 2);
				logger.info("firstCandle : {}",firstCandle);
				secondCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 1);
				logger.info("secondCandle : {}",secondCandle);
			}
			JSONArray response1 = cachedNiftyTradeValue;
			firstCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 2);
			secondCandle = cachedNiftyTradeValue.getJSONArray(cachedNiftyTradeValue.length() - 1);


			if (response1 == null || response1.length() < 7) {
				if (response1 != null) {
					logger.info("Not enough candles. Candles Formed: {}", response1.length());
				} else {
					logger.info("Not enough candles.");
				}
				cooldownMillis = 1_000L;
				return; 
			}
			// Extract last 6 candles
			List<Double> closes = new ArrayList<>();

			

			double firstHigh = firstCandle.getDouble(2);
			double firstLow = firstCandle.getDouble(3);
			double secondHigh = secondCandle.getDouble(2);
			double secondLow = secondCandle.getDouble(3);
			double secondOpen = secondCandle.getDouble(1);
			double secondClose = secondCandle.getDouble(4);


			String optionType = null;
			if (!positionTaken.get()) {
				if (secondHigh > firstHigh && secondLow > firstLow && secondClose > secondOpen) {
					optionType = "CE";
				} else if (secondLow < firstLow && secondHigh < firstHigh && secondClose < secondOpen) {
					optionType = "PE";
				}

				if(optionType == null) {
					System.out.println("Index Basic Trend pattern not formed.");
					cooldownMillis = 1_000L;
					return;
				}

				if (optionType != null) {
					// ✅ RSI Filter
					JSONArray currentResponse = fetchMarketCurrentValue(smartConnect);
					
					if (currentResponse == null || currentResponse.length() < 7) {
						if (currentResponse != null) {
							logger.info("Not enough candles. Candles Formed: {}", currentResponse.length());
						} else {
							logger.info("Not enough candles.");
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
					logger.info("firstCandle : {}",firstCandle);
					secondCandle = currentResponse.getJSONArray(currentResponse.length() - 1);
					logger.info("secondCandle : {}",secondCandle);
					
					double rsi = indServices.calculateRSI(closes, 5);
					if ((optionType.equals("CE") && rsi > 70 && !(rsi >= 80)) ||(optionType.equals("PE") && rsi < 30)) {
						logger.info("RSI filter failed: " + rsi);
						cooldownMillis = 1_000L;
						return;
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
						 targetPercent = 2.0;
						// 👇 Throttle fetchNiftyValue(optionToken) to once every 5 minutes
						 JSONArray optionFirstCandle = null;
						 JSONArray optionSecondCandle = null;
						if (nowIstMillis - lastOptionDataFetchTime > 5 * 60 * 1000 || cachedOptionData == null) {
						    cachedOptionData = fetchNiftyValue(optionToken, smartConnect);
						    lastOptionDataFetchTime = nowIstMillis;
						    optionFirstCandle = cachedOptionData.getJSONArray(cachedOptionData.length() - 2);
							logger.info("optionFirstCandle : {}",optionFirstCandle);
							optionSecondCandle = cachedOptionData.getJSONArray(cachedOptionData.length() - 1);
							logger.info("optionSecondCandle : {}",optionSecondCandle);
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
									logger.info("Not enough candles. Candles Formed: {}", optionCurrentData.length());
								} else {
									logger.info("Not enough candles.");
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
							
							optionFirstCandle = optionCurrentData.getJSONArray(optionCurrentData.length() - 2);
							logger.info("optionFirstCandle : {}",optionFirstCandle);
							optionSecondCandle = optionCurrentData.getJSONArray(optionCurrentData.length() - 1);
							logger.info("optionSecondCandle : {}",optionSecondCandle);
								
							if (optionSecondHigh > optionFirstHigh && optionSecondLow > optionFirstLow && optionSecondHigh != optionSecondClose) {
								if (optionSecondClose <= vwapOption) {
									logger.info("VWAP option Filter Failed: {}, optionSecondClose: {}", vwapOption, optionSecondClose);
									cooldownMillis = 1_000L;
									return;
								}
							} else {
								logger.info("Option Basic Trend pattern not formed.");
								cooldownMillis = 1_000L;
								return;
							}

							// ✅ Option RSI Filter
							double rsiOption = indServices.calculateRSI(closesofOption, 5);

							if (!botHelper.isOptionRSIFilterPassed(rsiOption)) {
								cooldownMillis = 1_000L;
								return;
							}

							double atrValue = indServices.calculateATR(optionData, 6);
							logger.info("ATR: {}", atrValue);

							List<Double> sarValues = indServices.calculateParabolicSAR(optionHighs, optionLows, true);
							double latestSar = sarValues.get(sarValues.size() - 1);
							double latestClose = optionData.getJSONArray(optionData.length() - 1).getDouble(4);
							logger.info("latestSar: {}, latestSar: {}", latestSar, latestClose);


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
									logger.info("optionGreek: {}", optionResponse);
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

							if (strikeFound) {
								logger.info("Strike {} - IV: {}, Delta: {}, Gamma: {}, Theta: {}, Vega: {}", optionSTrikePrice, iv, delta, gamma, theta, vega);
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
									break;
								} else if (marketPr <= stoplossValue) {
									capitalUsed = marketPr * quantity;
									this.updateTradeExit(tradeId, capitalUsed, istNow());
									positionTaken.set(false);
									reached.set(false);
									cooldownMillis = 300_000L;
									break;
								}
								try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									e.printStackTrace();
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
	
	/**
     * Stops the bot gracefully.
     */
    public void stopBot() {
    	stopBot.set(true);
        reached.set(false);       // stops monitoring
        positionTaken.set(false); // resets position
        if (strategyTimer != null) {
            strategyTimer.cancel();
            strategyTimer.purge();
            strategyTimer = null;
            logger.info("✅ Strategy timer stopped successfully.");
        }

        logger.info("Trading bot stopped manually.");
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
    
}
