package com.shoplocker.fssai.dto;

public record MobileLoginSendOtpResponse(
        boolean sent,
        boolean passwordChangeRequired,
        int remainingSends,
        int resendAvailableInSeconds,
        String devOtp,
        String message
) {}
