package com.shoplocker.fssai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response for {@code POST /api/auth/mobile-hint}.
 *
 * <p>{@code registered} answers the one question the sign-in screen needs before it
 * opens the reset flow: does this email belong to an account that may self-service
 * reset its password? Unknown emails, manager accounts, MSME (Udyam) accounts and
 * disabled accounts all answer {@code false}, the same way they answer an empty
 * {@code mobileEnding}.</p>
 *
 * <p>{@code mobileEnding} holds the last three digits of the registered mobile
 * ("764") when {@code registered} is true, and {@code null} otherwise. Only the tail
 * is ever returned: enough to let a legitimate owner recognise their number, not
 * enough to address an SMS.</p>
 *
 * <p>A caller over the per-IP lookup ceiling gets HTTP 429 with
 * {@code retryAfterSeconds} on {@link FssaiErrorResponse} instead of a response, so
 * the client never mistakes "slow down" for "this email is not registered".</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MobileHintResponse {

    private boolean registered;
    private String mobileEnding;

    public MobileHintResponse() {}

    public MobileHintResponse(boolean registered, String mobileEnding) {
        this.registered = registered;
        this.mobileEnding = mobileEnding;
    }

    public boolean isRegistered() { return registered; }
    public void setRegistered(boolean registered) { this.registered = registered; }

    public String getMobileEnding() { return mobileEnding; }
    public void setMobileEnding(String mobileEnding) { this.mobileEnding = mobileEnding; }
}
