package com.shoplocker.fssai.scheduler;

import com.shoplocker.fssai.entity.DocumentType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Central copy bank + document display names for notification messages.
 * Templates use {doc} and {shop} placeholders (plus {date} / {days} for
 * expiry copy) and are selected by period + a caller-supplied variant seed
 * so wording rotates without repeating back-to-back.
 */
public final class NotificationCopy {

    public enum Period { MORNING, AFTERNOON, EVENING }

    private NotificationCopy() {}

    public static Period periodFor(LocalTime time) {
        int hour = time.getHour();
        if (hour < 12) return Period.MORNING;
        if (hour < 17) return Period.AFTERNOON;
        return Period.EVENING;
    }

    // ── Document names ─────────────────────────────────────────────────────

    public static String formatDocumentName(DocumentType type) {
        return switch (type) {
            case GST -> "GST Certificate";
            case PAN -> "PAN Card";
            case FSSAI_FOOD_LICENSE -> "FSSAI License";
            case MSME_CERTIFICATE -> "MSME Certificate";
            case TRADE_LICENSE -> "Trade License";
            case SHOP_ESTABLISHMENT -> "Shop Establishment Certificate";
            case FIRE_SAFETY -> "Fire Safety Certificate";
            case POLLUTION_CONTROL -> "Pollution Control Certificate";
            case DRUG_LICENSE -> "Drug License";
            case IEC -> "IEC Certificate";
            case TRADEMARK -> "Trademark Certificate";
            case PROPERTY_TAX -> "Property Tax Receipt";
            case PROFESSIONAL_TAX -> "Professional Tax Certificate";
            case LABOUR_LICENSE -> "Labour License";
            case SHOP_INSURANCE -> "Shop Insurance";
            case AADHAAR -> "Aadhaar Card";
            case CUSTOM -> "Custom Document";
        };
    }

    // ── Missing-document drip copy ─────────────────────────────────────────

    private static final Map<Period, List<String>> GENERIC_MISSING = Map.of(
            Period.MORNING, List.of(
                    "Good morning! Your {doc} for {shop} is still pending - upload it now and check it off the list.",
                    "I think you forgot to upload your {doc}. Upload it now and keep {shop} fully compliant.",
                    "{shop} is missing its {doc}. Start the day by getting it done - upload now."
            ),
            Period.AFTERNOON, List.of(
                    "Quick check - your {doc} for {shop} hasn't been uploaded yet. Upload now to complete your profile.",
                    "Your {shop} still needs a {doc}. It only takes a minute - upload it now.",
                    "Missing {doc} for {shop}. Upload it now to stay compliant."
            ),
            Period.EVENING, List.of(
                    "Before you wrap up tonight - your {doc} for {shop} is still pending. Upload it now.",
                    "Started business without a {doc}? That can get you in trouble. Apply with us - upload it now.",
                    "Don't let a missing {doc} slow {shop} down. Upload it now."
            )
    );

    /** Doc-specific, period-specific wording (the high-conversion ones). */
    private static final Map<DocumentType, Map<Period, List<String>>> SPECIFIC_MISSING = Map.of(
            DocumentType.PAN, Map.of(
                    Period.MORNING, List.of(
                            "I think you forgot to upload your PAN card - upload it now.",
                            "Looks like your PAN card is still missing. Upload it now, it takes under a minute."
                    ),
                    Period.AFTERNOON, List.of(
                            "Your PAN card hasn't been uploaded yet - upload it now to complete your profile."
                    )
            ),
            DocumentType.GST, Map.of(
                    Period.AFTERNOON, List.of(
                            "Missing GST certificate - upload it to complete your profile.",
                            "Your GST certificate is pending. Upload it now to complete {shop}'s profile."
                    ),
                    Period.MORNING, List.of(
                            "I think you forgot to upload your GST certificate - upload it now."
                    )
            ),
            DocumentType.MSME_CERTIFICATE, Map.of(
                    Period.EVENING, List.of(
                            "Started business without MSME certificate? That can get you in trouble - apply with us, upload it now.",
                            "Your MSME certificate is still missing. Apply with us and upload it now."
                    )
            )
    );

    public static String missingDocTitle(DocumentType type) {
        return "\uD83D\uDCC4 Missing " + formatDocumentName(type);
    }

    public static String missingDocBody(DocumentType type, Period period, String shopName, int variant) {
        List<String> templates = SPECIFIC_MISSING
                .getOrDefault(type, Map.of())
                .get(period);
        if (templates == null) {
            templates = GENERIC_MISSING.get(period);
        }
        String template = templates.get(Math.floorMod(variant, templates.size()));
        return template
                .replace("{doc}", formatDocumentName(type))
                .replace("{shop}", shopName);
    }

    // ── Expiry-alert escalation copy ───────────────────────────────────────

    /** Stage identifiers also used as dedupe-key suffixes. */
    public static final String STAGE_T30 = "T30";
    public static final String STAGE_T15 = "T15";
    public static final String STAGE_T7 = "T7";

    public static String expiryTitle(long daysUntilExpiry, DocumentType type) {
        String docName = formatDocumentName(type);
        if (daysUntilExpiry < 0) return "\u274C " + docName + " expired";
        if (daysUntilExpiry == 0) return "\uD83D\uDEA8 " + docName + " expires today";
        if (daysUntilExpiry <= 7) return "\uD83D\uDEA8 " + docName + " expiring in " + daysUntilExpiry + " day" + (daysUntilExpiry == 1 ? "" : "s");
        if (daysUntilExpiry <= 15) return "\u26A0\uFE0F " + docName + " expiring in " + daysUntilExpiry + " days";
        return "\u26A0\uFE0F " + docName + " expiring soon";
    }

    public static String expiryBody(String stage, long daysUntilExpiry, String expiryDateStr,
                                    DocumentType type, String shopName, int variant) {
        return switch (stage) {
            case STAGE_T30 -> pick(List.of(
                    "Your {doc} for {shop} will expire on {date} - renew it now to avoid any trouble.",
                    "Heads up: the {doc} for {shop} expires on {date}. Renew now to stay worry-free."
            ), variant)
                    .replace("{date}", expiryDateStr)
                    .replace("{doc}", formatDocumentName(type))
                    .replace("{shop}", shopName);
            case STAGE_T15 -> pick(List.of(
                    "I think you forgot to renew your {doc} for {shop} - you still have time, renew it now.",
                    "Half the time is gone: your {doc} for {shop} expires on {date}. Renew it now."
            ), variant)
                    .replace("{date}", expiryDateStr)
                    .replace("{doc}", formatDocumentName(type))
                    .replace("{shop}", shopName);
            case STAGE_T7 -> pick(List.of(
                    "Very little time left to renew your {doc} for {shop} - don't worry, we will help you.",
                    "Just 7 days left for the {doc} of {shop}. Don't worry, we will help you - renew now."
            ), variant)
                    .replace("{doc}", formatDocumentName(type))
                    .replace("{shop}", shopName);
            case "DAILY" -> pick(List.of(
                    "Your {doc} for {shop} expires in {days} day{plural} - renew it now.",
                    "Only {days} day{plural} left to renew your {doc} for {shop}. Renew now to avoid trouble."
            ), variant)
                    .replace("{days}", String.valueOf(daysUntilExpiry))
                    .replace("{plural}", daysUntilExpiry == 1 ? "" : "s")
                    .replace("{doc}", formatDocumentName(type))
                    .replace("{shop}", shopName);
            case "DAILY_TODAY" -> pick(List.of(
                    "Your {doc} for {shop} expires TODAY - renew it now to avoid trouble.",
                    "Last day: the {doc} of {shop} expires today. Renew it right now!"
            ), variant)
                    .replace("{doc}", formatDocumentName(type))
                    .replace("{shop}", shopName);
            case "EXPIRED" -> pick(List.of(
                    "Your {doc} for {shop} expired {days} day{plural} ago. Upload the renewed copy ASAP to avoid penalties!",
                    "The {doc} of {shop} has been expired for {days} day{plural}. Renew it now to get back on track."
            ), variant)
                    .replace("{days}", String.valueOf(Math.abs(daysUntilExpiry)))
                    .replace("{plural}", Math.abs(daysUntilExpiry) == 1 ? "" : "s")
                    .replace("{doc}", formatDocumentName(type))
                    .replace("{shop}", shopName);
            default -> {
                // Unknown/extra T-stages reuse the at-least-30-days renew-by-date copy
                String template = pick(List.of(
                        "Your {doc} for {shop} will expire on {date} - renew it now to avoid any trouble.",
                        "Heads up: the {doc} for {shop} expires on {date}. Renew now to stay worry-free."
                ), variant)
                        .replace("{date}", expiryDateStr)
                        .replace("{doc}", formatDocumentName(type))
                        .replace("{shop}", shopName);
                yield template;
            }
        };
    }

    private static String pick(List<String> templates, int variant) {
        return templates.get(Math.floorMod(variant, templates.size()));
    }

    /** Stable pseudo-random variant index in [0, bound) from a seed. */
    public static int variant(long seed, LocalDate date, int bound) {
        int h = java.util.Objects.hash(seed, date.toString());
        return Math.floorMod(h, bound);
    }
}
