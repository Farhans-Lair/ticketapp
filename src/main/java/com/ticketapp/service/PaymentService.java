package com.ticketapp.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class PaymentService {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private RazorpayClient getClient() throws RazorpayException {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new RuntimeException(
                "Razorpay credentials missing. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        }
        return new RazorpayClient(keyId.trim(), keySecret.trim());
    }

    /**
     * Create a Razorpay order.
     * @param totalPaid amount in INR (will be converted to paise)
     * @param receipt   short unique label
     */
    public Order createOrder(double totalPaid, String receipt) throws RazorpayException {
        JSONObject options = new JSONObject();
        options.put("amount",          Math.round(totalPaid * 100));   // paise
        options.put("currency",        "INR");
        options.put("receipt",         receipt);
        options.put("payment_capture", 1);
        return getClient().orders.create(options);
    }

    /**
     * Verify Razorpay HMAC-SHA256 signature exactly as in payment.services.js.
     */
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String payload  = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.trim().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash     = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }
}
