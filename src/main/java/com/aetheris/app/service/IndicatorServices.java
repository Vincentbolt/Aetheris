package com.aetheris.app.service;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class IndicatorServices {

	private static final Logger logger = LoggerFactory.getLogger(IndicatorServices.class);
	private static final double RISK_FREE_RATE = 0.06; // 6%
	public  double extractStrikePriceFromSymbol(String symbol) {
		// Extract from symbol, e.g., NIFTY2450317500CE → 17500
		Pattern pattern = Pattern.compile("\\d{5}");
		Matcher matcher = pattern.matcher(symbol);
		if (matcher.find()) {
			return Double.parseDouble(matcher.group());
		}
		return 0;
	}



	public  double getTimeToExpiryInYears(Date expiryDate) {
		long millisToExpiry = expiryDate.getTime() - System.currentTimeMillis();
		double days = millisToExpiry / (1000.0 * 60 * 60 * 24);
		return days / 365.0;
	}



	public double calculateD1(double S, double K, double T, double sigma) {
		return (Math.log(S / K) + (RISK_FREE_RATE + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
	}



	public double calculateD2(double d1, double sigma, double T) {
		return d1 - sigma * Math.sqrt(T);
	}



	public double normalCDF(double x) {
		return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
	}



	public double calculateOptionPrice(String type, double S, double K, double T, double sigma) {
		double d1 = calculateD1(S, K, T, sigma);
		double d2 = calculateD2(d1, sigma, T);
		if (type.equalsIgnoreCase("CE")) {
			return S * normalCDF(d1) - K * Math.exp(-RISK_FREE_RATE * T) * normalCDF(d2);
		} else {
			return K * Math.exp(-RISK_FREE_RATE * T) * normalCDF(-d2) - S * normalCDF(-d1);
		}
	}



	public double calculateDelta(String type, double S, double K, double T, double sigma) {
		double d1 = calculateD1(S, K, T, sigma);
		return type.equalsIgnoreCase("CE") ? normalCDF(d1) : -normalCDF(-d1);
	}



	public double calculateGamma(double S, double K, double T, double sigma) {
		double d1 = calculateD1(S, K, T, sigma);
		return normalPDF(d1) / (S * sigma * Math.sqrt(T));
	}



	public double calculateTheta(String type, double S, double K, double T, double sigma) {
		double d1 = calculateD1(S, K, T, sigma);
		double d2 = calculateD2(d1, sigma, T);
		double term1 = -(S * normalPDF(d1) * sigma) / (2 * Math.sqrt(T));
		double term2 = RISK_FREE_RATE * K * Math.exp(-RISK_FREE_RATE * T);
		if (type.equalsIgnoreCase("CE")) {
			return term1 - term2 * normalCDF(d2);
		} else {
			return term1 + term2 * normalCDF(-d2);
		}
	}



	public  double calculateVega(double S, double K, double T, double sigma) {
		double d1 = calculateD1(S, K, T, sigma);
		return S * normalPDF(d1) * Math.sqrt(T);
	}



	public double calculateImpliedVolatility(String optionType, double marketPrice, double spotPrice, double strikePrice, double timeToExpiryYears) {
		final double TOLERANCE = 1e-5;
		final int MAX_ITERATIONS = 100;
		final double RISK_FREE_RATE = 0.06; // 6% annual rate
		final double MIN_VOL = 0.05;
		final double MAX_VOL = 1.0;

		double sigma = 0.2; // initial guess (20%)

		for (int i = 0; i < MAX_ITERATIONS; i++) {
			double d1 = (Math.log(spotPrice / strikePrice) + (RISK_FREE_RATE + 0.5 * sigma * sigma) * timeToExpiryYears)
					/ (sigma * Math.sqrt(timeToExpiryYears));
			double d2 = d1 - sigma * Math.sqrt(timeToExpiryYears);

			double optionPrice;
			double vega;

			if (optionType.equalsIgnoreCase("CE")) {
				optionPrice = spotPrice * cumulativeNormal(d1) - strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiryYears) * cumulativeNormal(d2);
			} else if (optionType.equalsIgnoreCase("PE")) {
				optionPrice = strikePrice * Math.exp(-RISK_FREE_RATE * timeToExpiryYears) * cumulativeNormal(-d2) - spotPrice * cumulativeNormal(-d1);
			} else {
				throw new IllegalArgumentException("Invalid option type: " + optionType);
			}

			vega = spotPrice * normalPDF(d1) * Math.sqrt(timeToExpiryYears);

			double diff = optionPrice - marketPrice;

			// Debug (optional)
			// System.out.printf("Iter %d: sigma=%.5f, price=%.5f, diff=%.5f%n", i, sigma, optionPrice, diff);

			if (Math.abs(diff) < TOLERANCE) {
				break;
			}

			if (vega == 0) {
				break;
			}

			sigma = sigma - diff / vega;

			// Clamp sigma to avoid runaway
			if (sigma < MIN_VOL) sigma = MIN_VOL;
			if (sigma > MAX_VOL) sigma = MAX_VOL;
		}

		return sigma;
	}



	public double cumulativeNormal(double x) {
		return 0.5 * (1.0 + erf(x / Math.sqrt(2)));
	}



	public double erf(double x) {
		// Abramowitz & Stegun formula 7.1.26 approximation
		double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
		double[] a = {0.254829592, -0.284496736, 1.421413741, -1.453152027, 1.061405429};
		double erf = 1 - ((((a[4] * t + a[3]) * t + a[2]) * t + a[1]) * t + a[0]) * t * Math.exp(-x * x);
		return x >= 0 ? erf : -erf;
	}



	public double normalPDF(double x) {
		return (1.0 / Math.sqrt(2 * Math.PI)) * Math.exp(-0.5 * x * x);
	}



	public String generateNiftyString(String indexType, String strikeOption, String expiryDateWithMonthyear) {
		try (InputStream is = getClass().getClassLoader().getResourceAsStream("OpenAPIScripMaster.json")) {
			if (is == null) throw new FileNotFoundException("File not found in resources");

			String content = new String(toByteArray(is), StandardCharsets.UTF_8);
			JSONArray jsonArray = new JSONArray(content);
			return getSymbolString(indexType, strikeOption, jsonArray, expiryDateWithMonthyear);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}

	}



	public String getSymbolString(String indexType, String strikeOption,JSONArray jsonArray, String expiryDateWithMonthyear) {
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			if (expiryDateWithMonthyear.equals(jsonObject.optString("expiry")) && indexType.equals(jsonObject.optString("name")) && jsonObject.optString("symbol").endsWith(strikeOption)) {
				return jsonObject.optString("symbol");
			}
		}
		return null;
	}


	public String getToken(String niftyString) {
	    try (InputStream is = getClass().getClassLoader().getResourceAsStream("OpenAPIScripMaster.json")) {
	        if (is == null) throw new FileNotFoundException("File not found in resources");

	        String content = new String(toByteArray(is), StandardCharsets.UTF_8);
	        JSONArray jsonArray = new JSONArray(content);
	        return getTokenBySymbol(jsonArray, niftyString);
	    } catch (IOException e) {
	        e.printStackTrace();
	        return null;
	    }
	}

	private byte[] toByteArray(InputStream is) throws IOException {
	    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	    int nRead;
	    byte[] data = new byte[4096];
	    while ((nRead = is.read(data, 0, data.length)) != -1) {
	        buffer.write(data, 0, nRead);
	    }
	    return buffer.toByteArray();
	}


	public String getTokenBySymbol(JSONArray jsonArray, String symbol) {
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject jsonObject = jsonArray.getJSONObject(i);
			if (symbol.equals(jsonObject.optString("symbol"))) {
				return jsonObject.optString("token");
			}
		}
		return null;
	}



	public double calculateRSI(List<Double> closes, int period) {
		if (closes.size() < period + 1) {
			logger.warn("Not enough candles to calculate RSI. Needed: {}, Got: {}", (period + 1), closes.size());
			return 50.0;
		}

		double gain = 0, loss = 0;

		// Step 1: First period average
		for (int i = 1; i <= period; i++) {
			double change = closes.get(i) - closes.get(i - 1);
			if (change > 0) gain += change;
			else loss -= change;
		}

		double avgGain = gain / period;
		double avgLoss = loss / period;

		// Step 2: Continue smoothing for rest of data
		for (int i = period + 1; i < closes.size(); i++) {
			double change = closes.get(i) - closes.get(i - 1);
			double currentGain = change > 0 ? change : 0;
			double currentLoss = change < 0 ? -change : 0;

			avgGain = ((avgGain * (period - 1)) + currentGain) / period;
			avgLoss = ((avgLoss * (period - 1)) + currentLoss) / period;
		}

		if (avgLoss == 0) return 100.0; // Avoid divide by zero

		double rs = avgGain / avgLoss;
		return 100 - (100 / (1 + rs));
	}



	public double calculateATR(JSONArray candles, int period) throws JSONException {
		if (candles == null || candles.length() <= period) {
			throw new IllegalArgumentException("Not enough candle data for ATR");
		}

		List<Double> trueRanges = new ArrayList<>();

		for (int i = candles.length() - period; i < candles.length(); i++) {
			JSONArray currentCandle = candles.getJSONArray(i);
			JSONArray prevCandle = candles.getJSONArray(i - 1);

			double high = currentCandle.getDouble(2);
			double low = currentCandle.getDouble(3);
			double prevClose = prevCandle.getDouble(4);

			double tr1 = high - low;
			double tr2 = Math.abs(high - prevClose);
			double tr3 = Math.abs(low - prevClose);

			double trueRange = Math.max(tr1, Math.max(tr2, tr3));
			trueRanges.add(trueRange);
		}

		// Calculate average of TR values
		double atr = trueRanges.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		return atr;
	}



	public double calculateEMA(List<Double> prices, int period) {
		if (prices.size() < period) return 0;

		double multiplier = 2.0 / (period + 1);
		double ema = prices.get(0); // Start with the first price as seed

		for (int i = 1; i < prices.size(); i++) {
			ema = ((prices.get(i) - ema) * multiplier) + ema;
		}

		return ema;
	}



	public double[] calculateMACD(List<Double> closes) {
		int shortPeriod = 12;
		int longPeriod = 26;
		int signalPeriod = 9;

		if (closes.size() < longPeriod + signalPeriod) return new double[]{0.0, 0.0};

		double shortEMA = calculateEMA(closes, shortPeriod);
		double longEMA = calculateEMA(closes, longPeriod);
		double macdLine = shortEMA - longEMA;

		// Create MACD line list for signal line EMA
		List<Double> macdHistory = new ArrayList<>();
		for (int i = closes.size() - signalPeriod - 1; i < closes.size(); i++) {
			double shortE = calculateEMA(closes.subList(0, i), shortPeriod);
			double longE = calculateEMA(closes.subList(0, i), longPeriod);
			macdHistory.add(shortE - longE);
		}
		double signalLine = calculateEMA(macdHistory, signalPeriod);

		return new double[]{macdLine, signalLine};
	}



	private String escapeCSV(String input) {
		if (input.contains(",") || input.contains("\"")) {
			input = input.replace("\"", "\"\"");
			return "\"" + input + "\"";
		}
		return input;
	}



	public double[] calculateBollingerBands(List<Double> prices, int period) {

		if (prices.size() < period) return new double[]{0, 0, 0};

		double sum = 0;
		for (int i = prices.size() - period; i < prices.size(); i++) {
			sum += prices.get(i);
		}
		double sma = sum / period;

		double squaredDiffSum = 0;
		for (int i = prices.size() - period; i < prices.size(); i++) {
			squaredDiffSum += Math.pow(prices.get(i) - sma, 2);
		}

		double stdDev = Math.sqrt(squaredDiffSum / period);
		double upperBand = sma + (2 * stdDev);
		double lowerBand = sma - (2 * stdDev);

		return new double[]{sma, upperBand, lowerBand};
	}



	public double calculateStochastic(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
		int size = closes.size();
		if (size < period) return 50.0;

		double highestHigh = Collections.max(highs.subList(size - period, size));
		double lowestLow = Collections.min(lows.subList(size - period, size));
		double currentClose = closes.get(size - 1);

		if (highestHigh == lowestLow) return 50.0;

		return ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100;
	}



	public double calculateADX(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
		if (highs.size() < period + 1 || lows.size() < period + 1 || closes.size() < period + 1) return 0.0;

		List<Double> plusDMs = new ArrayList<>();
		List<Double> minusDMs = new ArrayList<>();
		List<Double> trs = new ArrayList<>();

		for (int i = 1; i < highs.size(); i++) {
			double highDiff = highs.get(i) - highs.get(i - 1);
			double lowDiff = lows.get(i - 1) - lows.get(i);

			double plusDM = (highDiff > lowDiff && highDiff > 0) ? highDiff : 0;
			double minusDM = (lowDiff > highDiff && lowDiff > 0) ? lowDiff : 0;

			double tr = Math.max(highs.get(i) - lows.get(i),
					Math.max(Math.abs(highs.get(i) - closes.get(i - 1)), Math.abs(lows.get(i) - closes.get(i - 1))));

			plusDMs.add(plusDM);
			minusDMs.add(minusDM);
			trs.add(tr);
		}

		double smoothedTR = average(trs.subList(0, period));
		double smoothedPlusDM = average(plusDMs.subList(0, period));
		double smoothedMinusDM = average(minusDMs.subList(0, period));

		double plusDI = 100 * smoothedPlusDM / smoothedTR;
		double minusDI = 100 * smoothedMinusDM / smoothedTR;

		double dx = 100 * Math.abs(plusDI - minusDI) / (plusDI + minusDI);
		return dx;
	}



	public double average(List<Double> list) {
		return list.stream().mapToDouble(a -> a).average().orElse(0);
	}



	public static double calculateSMA(List<Double> closes, int period) {
		if (closes == null || closes.size() < period) {
			return -1;
		}
		double sum = 0;
		for (int i = closes.size() - period; i < closes.size(); i++) {
			sum += closes.get(i);
		}
		return sum / period;
	}


	public void logTradeToCSVForEffStrat(String orderId, String buyTimeStamp, String symbol, double buyPrice, double exitPrice, double target,
			double stoploss, String result, double quantity, double capitalUsed, String stringValue,
			double vwap, double optionSecondClose, double rsi, double atrValue,double latestSar,double latestClose,double iv,double delta,double gamma,double theta,double vega,double vegaThetaRatio) {

		Path filePath = Paths.get(System.getProperty("user.home"), "Documents", "TradingLogs", "AetherBot.csv");
		boolean fileExists = Files.exists(filePath);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), true))) {
			if (!fileExists) {
				writer.write("Order ID,Buy Time,Sell Time,Symbol,Buy Price,Exit Price,Target,Stoploss,Result,Quantity,CapitalUsed,PROFIT OR LOSS,VWAP,optionSecondClose,RSI,ATR,latestSar,latestClose,iv,delta,gamma,theta,vega,vegaThetaRatio\n");
			}

			String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

			writer.write(String.format(Locale.US,
					"%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%.2f,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
					orderId, buyTimeStamp, timeStamp, symbol, buyPrice, exitPrice, target, stoploss, result,
					quantity, capitalUsed, stringValue, vwap, optionSecondClose, rsi, atrValue, latestSar,
					latestClose, iv, delta, gamma, theta, vega, vegaThetaRatio));


			writer.flush();
		} catch (IOException e) {
			logger.error("Error in logTradeToCSV: ", e);
		}
	}



	public void logTradeToCSV(String buyTime, String symbol, double buyPrice, double exitPrice, double target, double stoploss, String result,
			double quantity, double capitalUsed, String stringValue, double vwap, double firstVolume,
			double secondVolume, double rsi, double emaShort, double emaLong, double upperBand, double atrValue,double iv,double delta,double gamma,double theta,double vega) {
		String fileName = "C:\\Users\\vrkarputhara\\Documents\\TradingLogs\\TradingAppNewEMABollinger.csv";
		Path filePath = Paths.get(fileName);

		try {
			// Ensure directory exists
			Files.createDirectories(filePath.getParent());

			boolean fileExists = Files.exists(filePath);
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
				if (!fileExists) {
					writer.write("Buy time,Sell time,Symbol,Buy Price,Exit Price,Target,Stoploss,Result,Quantity,CapitalUsed,PROFIT OR LOSS,VWAP,First Volume,Second Volume,RSI,EMASHORT,EMALONG,UPPER BAND,ATR,iv,delta,gamma,theta,vega\n");
				}

				String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
				double pnl = (exitPrice - buyPrice) * quantity;

				writer.write(String.format(Locale.US,
						"%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
						buyTime, timeStamp, escapeCSV(symbol), buyPrice, exitPrice, target, stoploss, escapeCSV(result),
						quantity, capitalUsed, pnl, vwap, firstVolume, secondVolume, rsi, emaShort, emaLong, upperBand,atrValue,iv,delta,gamma,theta,vega));

				writer.flush();
			}
		} catch (IOException e) {
			logger.error("Error in logTradeToCSV: ", e);
		}
	}



	public synchronized void logTradeToCSV(String buyTimeStamp, String symbol, double buyPrice, double exitPrice,
			double target, double stoploss, String result, double quantity,
			double capitalUsed, String stringValue, double vwap, double firstVolume,
			double secondVolume, double[] macd, double stochastic, double adx, double rsi, double atrValue,double iv,double delta,double gamma,double theta,double vega) {

		Path filePath = Paths.get(System.getProperty("user.home"), "Documents", "TradingLogs", "TradingAppWithNineFilters.csv");
		boolean fileExists = Files.exists(filePath);

		double macdLine = macd[0];
		double signalLine = macd[1];

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), true))) {
			if (!fileExists) {
				writer.write("Buy Time,Sell Time,Symbol,Buy Price,Exit Price,Target,Stoploss,Result,Quantity,CapitalUsed,PROFIT OR LOSS,VWAP,First Volume,Second Volume,MACD Line,MACD Signal,STOCHASTIC,ADX,RSI,ATR,iv,delta,gamma,theta,vega\n");
			}

			String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

			writer.write(String.format(Locale.US,
					"%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%.2f,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
					buyTimeStamp, timeStamp, symbol, buyPrice, exitPrice, target, stoploss, result,
					quantity, capitalUsed, stringValue, vwap, firstVolume, secondVolume,
					macdLine, signalLine, stochastic, adx, rsi, atrValue,iv,delta,gamma,theta,vega));

			writer.flush();
		} catch (IOException e) {
			logger.error("Error writing to file: " + filePath.toString(), e);
		}
	}



	public synchronized void logTradeToCSV(String buyTimeStamp, String symbol, double buyPrice, double exitPrice,
			double target, double stoploss, String result, double quantity,
			double capitalUsed, String stringValue, double vwap, double firstVolume,
			double secondVolume, double stochastic, double adx, double macd1,
			double macd2, double rsi, double emaShort, double emaLong,
			double upperBand, double atrValue,double iv,double delta,double gamma,double theta,double vega) {

		Path filePath = Paths.get(System.getProperty("user.home"), "Documents", "TradingLogs", "TradingAppAll.csv");
		boolean fileExists = Files.exists(filePath);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), true))) {
			if (!fileExists) {
				writer.write("Buy Time,Sell Time,Symbol,Buy Price,Exit Price,Target,Stoploss,Result,Quantity,CapitalUsed,"
						+ "PROFIT OR LOSS,VWAP,First Volume,Second Volume,STOCHASTIC,ADX,MACD1,MACD2,RSI,EMASHORT,"
						+ "EMALONG,UPPERBAND,ATR,iv,delta,gamma,theta,vega\n");
			}

			String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

			writer.write(String.format(Locale.US,
					"%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%.2f,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
					buyTimeStamp, timeStamp, symbol, buyPrice, exitPrice, target, stoploss, result,
					quantity, capitalUsed, stringValue, vwap, firstVolume, secondVolume,
					stochastic, adx, macd1, macd2, rsi, emaShort, emaLong, upperBand,atrValue,iv,delta,gamma,theta,vega));

			writer.flush();
		} catch (IOException e) {
			logger.error("Error in logTradeToCSV: ", e);
		}
	}



	public void logTradeToCSV(String buyTimeStamp, String symbol, double buyPrice, double exitPrice, double target,
			double stoploss, String result, double quantity, double capitalUsed, String stringValue,
			double vwap, double rsi, double firstVolume, double secondVolume, double smaOption, double currentOptionClose,double atrValue,double iv,double delta,double gamma,double theta,double vega) {

		Path filePath = Paths.get(System.getProperty("user.home"), "Documents", "TradingLogs", "TradingAppNewWithEffStratWithSMA.csv");
		boolean fileExists = Files.exists(filePath);

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath.toFile(), true))) {
			if (!fileExists) {
				writer.write("Buy Time,Sell Time,Symbol,Buy Price,Exit Price,Target,Stoploss,Result,Quantity,CapitalUsed,PROFIT OR LOSS,VWAP,RSI,First Volume,Second Volume,SMA,Current Option Close,ATR,iv,delta,gamma,theta,vega\n");
			}

			String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

			writer.write(String.format(Locale.US,
					"%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%s,%.2f,%.2f,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
					buyTimeStamp, timeStamp, symbol, buyPrice, exitPrice, target, stoploss, result,
					quantity, capitalUsed, stringValue, vwap, rsi, firstVolume, secondVolume,smaOption,currentOptionClose,atrValue,iv,delta,gamma,theta,vega));

			writer.flush();
		} catch (IOException e) {
			logger.error("Error in logTradeToCSV: ", e);
		}
	}



	public int getMaxQuantityForIndex(String indexType) {
		switch (indexType.toUpperCase()) {
		case "MIDCPNIFTY": return 140000;
		case "BANKNIFTY": return 43750;
		case "FINNIFTY": return 87750;
		case "SENSEX": return 50000;
		case "NIFTY": return 90000;
		default: return 1000;
		}
	}


	public double getTimeToExpiryInYears(String expiryDateStr){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date expiryDate = null;
		try {
			expiryDate = sdf.parse(expiryDateStr);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Date today = new Date();

		long diffInMillis = expiryDate.getTime() - today.getTime();
		double diffInDays = (double) diffInMillis / (1000 * 60 * 60 * 24);

		return diffInDays / 365.0;
	}



	public double calculateOBV(List<Double> closes, List<Double> volumes) {
		double obv = 0;
		for (int i = 1; i < closes.size(); i++) {
			if (closes.get(i) > closes.get(i - 1)) {
				obv += volumes.get(i);
			} else if (closes.get(i) < closes.get(i - 1)) {
				obv -= volumes.get(i);
			}
			// else: same close, no change in OBV
		}
		return obv;
	}


	public List<Double> calculateParabolicSAR(List<Double> highs, List<Double> lows, boolean isUptrendStart) {
		List<Double> sarList = new ArrayList<>();

		double af = 0.02;
		double maxAf = 0.2;
		double ep = isUptrendStart ? highs.get(0) : lows.get(0);
		double sar = isUptrendStart ? lows.get(0) : highs.get(0);
		boolean isUptrend = isUptrendStart;

		for (int i = 1; i < highs.size(); i++) {
			sar = sar + af * (ep - sar);

			if (isUptrend) {
				if (highs.get(i) > ep) {
					ep = highs.get(i);
					af = Math.min(af + 0.02, maxAf);
				}
				if (lows.get(i) < sar) {
					isUptrend = false;
					sar = ep;
					ep = lows.get(i);
					af = 0.02;
				}
			} else {
				if (lows.get(i) < ep) {
					ep = lows.get(i);
					af = Math.min(af + 0.02, maxAf);
				}
				if (highs.get(i) > sar) {
					isUptrend = true;
					sar = ep;
					ep = highs.get(i);
					af = 0.02;
				}
			}

			sarList.add(sar);
		}

		return sarList;
	}

}
