package com.shoplocker.fssai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.entity.BusinessScale;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Primary-shop flag: the first shop created for an owner who had none is
 * marked primary; pre-existing accounts (already-owned shops with
 * {@code is_primary = false}) never gain one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Primary shop flag")
class ShopPrimaryTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ShopRepository shopRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;

    private static final String REGISTER_BODY = """
            {
              "userName": "Anjali Kashyap",
              "mobileNumber": "9876543210",
              "emailId": "anjali@example.com",
              "password": "Strong@123"
            }""";

    private static final String FIRST_SHOP_BODY = """
            {
              "shopName": "Anjali General Store",
              "ownerName": "Anjali Kashyap",
              "mobile": "1234567890",
              "category": "GROCERY",
              "scale": "SMALL",
              "state": "Tamil Nadu",
              "city": "Chennai"
            }""";

    private static final String SECOND_SHOP_BODY = """
            {
              "shopName": "Anjali Electronics",
              "ownerName": "Anjali Kashyap",
              "mobile": "2234567890",
              "category": "ELECTRONICS",
              "scale": "MEDIUM",
              "state": "Tamil Nadu",
              "city": "Chennai"
            }""";

    @BeforeEach
    void clean() {
        documentRepository.deleteAll();
        shopRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String register() throws Exception {
        MvcResult reg = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode createShop(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/shops")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("First shop created for a new profile is primary")
    void firstShopIsPrimary() throws Exception {
        String token = register();

        JsonNode shop = createShop(token, FIRST_SHOP_BODY);

        assertThat(shop.get("primary").asBoolean())
                .as("the owner's first shop must be the primary shop")
                .isTrue();
    }

    @Test
    @DisplayName("Second and later shops are never primary")
    void laterShopsAreNotPrimary() throws Exception {
        String token = register();

        JsonNode first = createShop(token, FIRST_SHOP_BODY);
        JsonNode second = createShop(token, SECOND_SHOP_BODY);

        assertThat(first.get("primary").asBoolean()).isTrue();
        assertThat(second.get("primary").asBoolean())
                .as("only the first shop of an owner may be primary")
                .isFalse();
    }

    @Test
    @DisplayName("GET /api/shops/my-shops returns the primary shop first with the flag")
    void myShopsListsPrimaryFirst() throws Exception {
        String token = register();
        createShop(token, FIRST_SHOP_BODY);
        createShop(token, SECOND_SHOP_BODY);

        MvcResult listResult = mockMvc.perform(get("/api/shops/my-shops")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].primary").value(false))
                .andReturn();

        JsonNode shops = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(shops.isArray()).isTrue();
        assertThat(shops).hasSize(2);
        assertThat(shops.get(0).get("shopName").asText()).isEqualTo("Anjali General Store");
    }

    @Test
    @DisplayName("Owner who already has shops never gets a new primary (legacy accounts)")
    void existingOwnerNeverGainsANewPrimary() throws Exception {
        String token = register();

        // Simulate pre-existing rows: is_primary stays false, exactly as the
        // no-backfill V15 migration leaves every legacy shop.
        User owner = userRepository.findByEmailId("anjali@example.com").orElseThrow();
        for (int i = 0; i < 2; i++) {
            Shop legacy = new Shop();
            legacy.setShopName("Legacy Shop " + i);
            legacy.setOwnerName("Anjali Kashyap");
            legacy.setMobile("334455667" + i);
            legacy.setCategory("GROCERY");
            legacy.setScale(BusinessScale.SMALL);
            legacy.setState("Tamil Nadu");
            legacy.setCity("Chennai");
            legacy.setOwner(owner);
            shopRepository.save(legacy);
        }

        JsonNode added = createShop(token, FIRST_SHOP_BODY);

        assertThat(added.get("primary").asBoolean())
                .as("an owner who already has shops must never gain a new primary")
                .isFalse();

        long primaryCount = shopRepository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getPrimary()))
                .count();
        assertThat(primaryCount).isZero();
    }
}
