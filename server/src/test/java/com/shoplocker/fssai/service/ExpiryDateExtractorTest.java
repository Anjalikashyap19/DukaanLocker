





import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExpiryDateExtractor unit tests")
class ExpiryDateExtractorTest {

    private final ExpiryDateExtractor extractor = new ExpiryDateExtractor();

    @Test
    void dayFirstSlashFormat() {
        Optional<LocalDateTime> result = extractor.extract(
                "FSSAI Food Licence No. 12345678901234\nValid upto: 30/10/2026");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 30, 0, 0));
    }

    @Test
    void dayFirstDashFormat() {
        Optional<LocalDateTime> result = extractor.extract(
                "Valid Till 15-04-2027");
        assertThat(result).contains(LocalDateTime.of(2027, 4, 15, 0, 0));
    }

    @Test
    void dayMonthNameYearFormat() {
        Optional<LocalDateTime> result = extractor.extract(
                "Date of Expiry: 30 October 2026");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 30, 0, 0));
    }

    @Test
    void dayAbbrMonthYearFormat() {
        Optional<LocalDateTime> result = extractor.extract(
                "Expiry Date 24-Oct-2026");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 24, 0, 0));
    }

    @Test
    void monthDayYearFormat() {
        Optional<LocalDateTime> result = extractor.extract(
                "Valid until October 30, 2026");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 30, 0, 0));
    }

    @Test
    void yearFirstIsoFormat() {
        Optional<LocalDateTime> result = extractor.extract(
                "expiry date: 2026-10-30");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 30, 0, 0));
    }

    @Test
    void validFromToPicksTheEndOfRange() {
        Optional<LocalDateTime> result = extractor.extract(
                "Validity: Valid From 01-04-2023 To 31-03-2027");
        assertThat(result).contains(LocalDateTime.of(2027, 3, 31, 0, 0));
    }

    @Test
    void monthYearOnlyFallsBackToLastDayOfMonth() {
        Optional<LocalDateTime> result = extractor.extract(
                "Valid till Oct 2026");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 31, 0, 0));
    }

    @Test
    void lifetimeMeansNoExpiry() {
        assertThat(extractor.extract("Valid upto: Lifetime")).isEmpty();
        assertThat(extractor.extract("Valid till life time")).isEmpty();
    }

    @Test
    void noValidityKeywordIgnoresDates() {
        // A date without a validity keyword (issue date, print date) is not an expiry
        assertThat(extractor.extract("Issued on 30/10/2020 at Bengaluru")).isEmpty();
    }

    @Test
    void noDateAfterKeywordMeansEmpty() {
        assertThat(extractor.extract("Period of Validity: NA")).isEmpty();
        assertThat(extractor.extract("")).isEmpty();
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void implausibleYearsAreRejected() {
        assertThat(extractor.extract("Valid till 30/10/1975")).isEmpty();
        assertThat(extractor.extract("Valid till 30/10/2095")).isEmpty();
    }

    @Test
    void issueDateKeywordDoesNotLeakIntoExpiry() {
        // "Date of Issue" must not be matched; the expiry keyword drives extraction
        Optional<LocalDateTime> result = extractor.extract(
                "Date of Issue: 01/04/2023\nDate of Expiry: 31/03/2027");
        assertThat(result).contains(LocalDateTime.of(2027, 3, 31, 0, 0));
    }

    @Test
    @DisplayName("state trade licences say 'in force until', not 'valid till'")
    void inForceUntilPhrasing() {
        Optional<LocalDateTime> result = extractor.extract(
                "PERMANENT CERTIFICATE OF ENLISTMENT\n"
                        + "This certificate will be in force until the 03-Oct-2026.\n"
                        + "Date of Issue: 04-Oct-2025");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 3, 0, 0));
    }

    @Test
    @DisplayName("a trailing 'to' in the surrounding prose must not discard the date")
    void trailingToDoesNotDestroyTheDate() {
        // Before the "only truncate when a date follows" rule, the " to " inside
        // "liable to be produced" re-scoped the window onto prose and the expiry
        // came back empty, so no renewal notification was ever scheduled.
        Optional<LocalDateTime> result = extractor.extract(
                "in force until the 03-Oct-2026 and is liable to be produced"
                        + " at the time of renewal");
        assertThat(result).contains(LocalDateTime.of(2026, 10, 3, 0, 0));
    }
}
