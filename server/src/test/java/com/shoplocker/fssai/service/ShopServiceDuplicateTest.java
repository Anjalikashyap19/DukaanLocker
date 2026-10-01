package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two guards that keep one account from holding the same business, or the
 * same paperwork, twice:
 *
 * <ol>
 *   <li>{@link ShopService#assertNoDuplicateShop} — name matching, now
 *       token-subset rather than exact, so a shortened name ("Sehgal") can no
 *       longer slip past a longer one ("Sehgal Automobiles").</li>
 *   <li>{@link ShopService#assertDocumentNotReused} — content-addressed, so the
 *       very same file cannot be filed under a second shop of the account.</li>
 * </ol>
 *
 * <p>Mocked at the repository seam: these assert the matching rules, not JPA.
 * The end-to-end wiring is covered by {@code AuthIntegrationTest}.</p>
 */
class ShopServiceDuplicateTest {

    private static final String HASH = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

    private ShopRepository shopRepository;
    private DocumentRepository documentRepository;
    private ShopService service;

    @BeforeEach
    void setUp() {
        shopRepository = mock(ShopRepository.class);
        documentRepository = mock(DocumentRepository.class);
        service = new ShopService();
        ReflectionTestUtils.setField(service, "shopRepository", shopRepository);
        ReflectionTestUtils.setField(service, "documentRepository", documentRepository);
    }

    private static Shop shop(Long id, String name) {
        Shop s = new Shop();
        s.setShopName(name);
        if (id != null) {
            ReflectionTestUtils.setField(s, "id", id);
        }
        return s;
    }

    // ========================================================================
    // 1. Name matching
    // ========================================================================

    @Nested
    @DisplayName("isSameBusinessName — token subset in either direction")
    class SameBusinessName {

        @ParameterizedTest(name = "[{index}] \"{0}\" vs \"{1}\" -> {2}")
        @CsvSource({
                // the case the user actually hit
                "Sehgal Automobiles, Sehgal, true",
                "Sehgal, Sehgal Automobiles, true",
                // longer name added on top of a shorter existing one
                "Sehgal, Sehgal Automobiles Spare Parts, true",
                "Sehgal Automobiles Spare Parts, Sehgal, true",
                // exact equality, in the shapes a user types
                "Anjali General Store, Anjali General Store, true",
                "Anjali General Store, anjali   GENERAL store, true",
                "Anjali General Store, ANJALI GENERAL STORE, true",
                // punctuation and diacritics fold away before comparison
                "Anjali General Store, Ánjali General–Store!!, true",
                // genuinely different businesses — neither word set contains the other
                "Anjali General Store, Anjali Electronics, false",
                "Anjali Electronics, Anjali General Store, false",
                "Shah Motors, Traders Corner, false",
                "New Market, Diya Biriyani, false"
        })
        void rule(String left, String right, boolean expected) {
            assertThat(ShopService.isSameBusinessName(left, right))
                    .as("\"%s\" vs \"%s\"", left, right)
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("a name that folds to nothing matches nothing, not even itself")
        void emptyNamesNeverMatch() {
            assertThat(ShopService.isSameBusinessName(null, null)).isFalse();
            assertThat(ShopService.isSameBusinessName("", "")).isFalse();
            assertThat(ShopService.isSameBusinessName("   ", "Sehgal")).isFalse();
            assertThat(ShopService.isSameBusinessName("Sehgal", "")).isFalse();
            assertThat(ShopService.isSameBusinessName(null, null)).isFalse();
        }

        @Test
        @DisplayName("matching is symmetric")
        void isSymmetric() {
            assertThat(ShopService.isSameBusinessName("Sehgal Automobiles", "Sehgal"))
                    .isEqualTo(ShopService.isSameBusinessName("Sehgal", "Sehgal Automobiles"));
        }
    }

    // ========================================================================
    // 2. assertNoDuplicateShop
    // ========================================================================

    @Nested
    @DisplayName("assertNoDuplicateShop — create, rename and MSME onboarding")
    class DuplicateShop {

        @Test
        @DisplayName("identical name returns 409 duplicate_shop naming the existing business")
        void exactMatchRejected() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Anjali General Store")));

            assertThatThrownBy(() -> service.assertNoDuplicateShop(7L, "Anjali General Store", "Main Branch", null))
                    .isInstanceOfSatisfying(FssaiException.class, e -> {
                        assertThat(e.getFailureCode()).isEqualTo(FailureCode.DUPLICATE_SHOP);
                        assertThat(e.getMessage()).contains("already exists");
                        assertThat(e.getMessage()).contains("Anjali General Store");
                    });
        }

        @Test
        @DisplayName("shortened name against a longer existing one is rejected")
        void subsetAgainstExistingRejected() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Sehgal Automobiles")));

            assertThatThrownBy(() -> service.assertNoDuplicateShop(7L, "Sehgal", "Main Branch", null))
                    .isInstanceOfSatisfying(FssaiException.class, e ->
                            assertThat(e.getFailureCode()).isEqualTo(FailureCode.DUPLICATE_SHOP));
        }

        @Test
        @DisplayName("longer name added on top of an existing shorter one is rejected")
        void supersetAgainstExistingRejected() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Sehgal")));

            assertThatThrownBy(() -> service.assertNoDuplicateShop(7L, "Sehgal Automobiles", "Main Branch", null))
                    .isInstanceOfSatisfying(FssaiException.class, e ->
                            assertThat(e.getFailureCode()).isEqualTo(FailureCode.DUPLICATE_SHOP));
        }

        @Test
        @DisplayName("the error names the existing shop, not the one being typed")
        void messageNamesExistingShop() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Sehgal Automobiles")));

            assertThatThrownBy(() -> service.assertNoDuplicateShop(7L, "Sehgal", null, null))
                    .isInstanceOfSatisfying(FssaiException.class, e ->
                            assertThat(e.getMessage()).contains("Sehgal Automobiles"));
        }

        @Test
        @DisplayName("a branch label never distinguishes two businesses")
        void branchIsIgnored() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Sehgal Automobiles")));

            assertThatThrownBy(() -> service.assertNoDuplicateShop(
                    7L, "Sehgal", "Anna Nagar Branch", null))
                    .isInstanceOfSatisfying(FssaiException.class, e ->
                            assertThat(e.getFailureCode()).isEqualTo(FailureCode.DUPLICATE_SHOP));
        }

        @Test
        @DisplayName("names whose word sets merely overlap are both allowed")
        void overlappingButDisjointAllowed() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Anjali General Store")));

            assertThatCode(() -> service.assertNoDuplicateShop(
                    7L, "Anjali Electronics", "Main Branch", null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the shop being edited is excluded, so a no-op rename stays legal")
        void excludedShopIsSkipped() {
            when(shopRepository.findByOwnerId(7L)).thenReturn(List.of(shop(1L, "Sehgal Automobiles")));

            assertThatCode(() -> service.assertNoDuplicateShop(
                    7L, "Sehgal Automobiles", "Main Branch", 1L))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a name that folds to nothing is treated as absent")
        void blankNameNeverCollides() {
            assertThatCode(() -> service.assertNoDuplicateShop(7L, "   ", null, null))
                    .doesNotThrowAnyException();
            verify(shopRepository, never()).findByOwnerId(anyLong());
        }
    }

    // ========================================================================
    // 3. assertDocumentNotReused
    // ========================================================================

    @Nested
    @DisplayName("assertDocumentNotReused — the same file under two shops")
    class DocumentReuse {

        private Document collidingDocument(String otherShopName) {
            Shop other = shop(99L, otherShopName);
            Document d = new Document(other, DocumentType.TRADE_LICENSE);
            return d;
        }

        @Test
        @DisplayName("identical bytes on another shop of the same account are rejected")
        void sameBytesOnOtherShopRejected() {
            when(documentRepository.findByContentHashForOtherShopOfSameOwner(HASH, 5L))
                    .thenReturn(List.of(collidingDocument("New Market")));

            assertThatThrownBy(() -> service.assertDocumentNotReused(5L, HASH))
                    .isInstanceOfSatisfying(FssaiException.class, e -> {
                        assertThat(e.getFailureCode()).isEqualTo(FailureCode.DUPLICATE_DOCUMENT);
                        assertThat(e.getMessage()).contains("New Market");
                        assertThat(e.getMessage()).contains("already filed");
                    });
        }

        @Test
        @DisplayName("no collision elsewhere means the upload proceeds")
        void noCollisionAllowed() {
            when(documentRepository.findByContentHashForOtherShopOfSameOwner(HASH, 5L))
                    .thenReturn(List.of());

            assertThatCode(() -> service.assertDocumentNotReused(5L, HASH))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a null hash means nothing to compare and never queries")
        void nullHashSkipsTheLookup() {
            assertThatCode(() -> service.assertDocumentNotReused(5L, null))
                    .doesNotThrowAnyException();
            verify(documentRepository, never())
                    .findByContentHashForOtherShopOfSameOwner(anyString(), anyLong());
        }

        @Test
        @DisplayName("a blank hash means nothing to compare and never queries")
        void blankHashSkipsTheLookup() {
            assertThatCode(() -> service.assertDocumentNotReused(5L, "   "))
                    .doesNotThrowAnyException();
            verify(documentRepository, never())
                    .findByContentHashForOtherShopOfSameOwner(anyString(), anyLong());
        }

        @Test
        @DisplayName("the query is always scoped to the shop being uploaded to")
        void lookupIsScopedToTheTargetShop() {
            when(documentRepository.findByContentHashForOtherShopOfSameOwner(HASH, 5L))
                    .thenReturn(List.of());

            service.assertDocumentNotReused(5L, HASH);

            verify(documentRepository).findByContentHashForOtherShopOfSameOwner(HASH, 5L);
        }
    }
}
