package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code POST /api/auth/mobile-hint}.
 *
 * <p>{@code mobileEnding} holds the last three digits of the registered mobile
 * ("764") when the email belongs to an account that may self-service reset its
 * password, and {@code null} otherwise — unknown email, manager account, MSME
 * (Udyam) account, disabled account. Only the tail is ever returned: enough to
 * let a legitimate owner recognise their number, not enough to address an SMS.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileHintResponse {

    private String mobileEnding;

    public MobileHintResponse() {}

    public MobileHintResponse(String mobileEnding) {
        this.mobileEnding = mobileEnding;
    }

    public String getMobileEnding() { return mobileEnding; }
    public void setMobileEnding(String mobileEnding) { this.mobileEnding = mobileEnding; }
}
