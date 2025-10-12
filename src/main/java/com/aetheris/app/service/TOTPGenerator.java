/**
 * 
 */
package com.aetheris.app.service;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

/**
 * 
 */
public class TOTPGenerator {

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_IN_SECONDS = 30;

    public static String generateTOTPCode(String secretKey) {
        try {
            Base32 base32 = new Base32();
            byte[] keyBytes = base32.decode(secretKey);

            long currentTimeInSeconds = System.currentTimeMillis() / 1000L;
            long timeStep = currentTimeInSeconds / TIME_STEP_IN_SECONDS;

            byte[] timeStepBytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                timeStepBytes[i] = (byte) (timeStep & 0xFF);
                timeStep >>= 8;
            }

            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, HMAC_SHA1_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(timeStepBytes);

            int offset = hash[hash.length - 1] & 0xF;
            int binaryCode = (hash[offset] & 0x7F) << 24 |
                    (hash[offset + 1] & 0xFF) << 16 |
                    (hash[offset + 2] & 0xFF) << 8 |
                    (hash[offset + 3] & 0xFF);


            int otp = binaryCode % 1000000;
            return String.format("%06d", otp);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Handle the exception appropriately, e.g., log the error and return a default value or throw a custom exception
            e.printStackTrace();
            return "Error generating TOTP";
        }
    }
}
