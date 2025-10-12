package com.aetheris.app.service;

import org.springframework.stereotype.Service;

@Service
public class BlackScholesHelper {

    private static double cdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    private static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double[] a = {0.254829592, -0.284496736, 1.421413741, -1.453152027, 1.061405429};
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * Math.pow(t, i + 1);
        }
        double result = 1 - sum * Math.exp(-x * x);
        return x >= 0 ? result : -result;
    }

    public static double d1(double S, double K, double r, double sigma, double T) {
        return (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
    }

    public static double d2(double d1, double sigma, double T) {
        return d1 - sigma * Math.sqrt(T);
    }

    public static double blackScholesPrice(double S, double K, double r, double sigma, double T, boolean isCall) {
        double d1 = d1(S, K, r, sigma, T);
        double d2 = d2(d1, sigma, T);
        if (isCall) {
            return S * cdf(d1) - K * Math.exp(-r * T) * cdf(d2);
        } else {
            return K * Math.exp(-r * T) * cdf(-d2) - S * cdf(-d1);
        }
    }

    public static double impliedVolatility(double marketPrice, double S, double K, double T, double r, boolean isCall) {
        double tolerance = 1e-6;
        double sigma = 0.2; // Initial guess
        int maxIterations = 100;

        for (int i = 0; i < maxIterations; i++) {
            double price = blackScholesPrice(S, K, r, sigma, T, isCall);
            double vega = vega(S, K, r, sigma, T);
            if (vega == 0) break;

            double diff = price - marketPrice;
            if (Math.abs(diff) < tolerance) {
                return sigma;
            }

            sigma -= diff / vega;
            if (sigma <= 0) sigma = tolerance;
        }

        throw new RuntimeException("IV calculation did not converge.");
    }

    public static double delta(double S, double K, double r, double sigma, double T, boolean isCall) {
        double d1 = d1(S, K, r, sigma, T);
        return isCall ? cdf(d1) : cdf(d1) - 1;
    }

    public static double gamma(double S, double K, double r, double sigma, double T) {
        double d1 = d1(S, K, r, sigma, T);
        return Math.exp(-0.5 * d1 * d1) / (S * sigma * Math.sqrt(2 * Math.PI * T));
    }

    public static double theta(
    	    double S, double K, double r, double sigma, double T, boolean isCall
    		) {
    	double d1 = d1(S, K, r, sigma, T);
    	double d2 = d2(d1, sigma, T);

    	double phiD1 = Math.exp(-0.5 * d1 * d1) / Math.sqrt(2 * Math.PI);
    	double firstTerm = -(S * phiD1 * sigma) / (2 * Math.sqrt(T));

    	double secondTerm = r * K * Math.exp(-r * T);

    	if (isCall) {
    		return firstTerm - secondTerm * cdf(d2);
    	} else {
    		return firstTerm + secondTerm * cdf(-d2);
    	}
    }


    public static double vega(double S, double K, double r, double sigma, double T) {
        double d1 = d1(S, K, r, sigma, T);
        return S * Math.exp(-0.5 * d1 * d1) * Math.sqrt(T) / Math.sqrt(2 * Math.PI);
    }
}

