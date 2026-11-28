package com.shoplocker.fssai.dto;

public record MobileLoginSetPasswordResponse(
        boolean success,
        String message,
        String token,
        String tokenType,
        Long userId,
        String userName,
        String mobileNumber,
        String emailId,
        String role
) {}
