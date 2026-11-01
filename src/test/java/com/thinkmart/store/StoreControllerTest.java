package com.thinkmart.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:thinkmart-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class StoreControllerTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void catalogIsServedByTheJavaBackend() {
        ResponseEntity<StoreService.Product[]> response =
                http.getForEntity("/api/products", StoreService.Product[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(8, response.getBody().length);
        assertEquals("laptop", response.getBody()[0].id());
    }

    @Test
    void applicationServesItsStorefrontAssets() {
        ResponseEntity<String> home = http.getForEntity("/", String.class);
        ResponseEntity<String> page = http.getForEntity("/pages/landingpage.html", String.class);
        ResponseEntity<String> styles = http.getForEntity("/Styles/store.css", String.class);
        ResponseEntity<String> script = http.getForEntity("/scripts/store.js", String.class);

        assertEquals(HttpStatus.OK, home.getStatusCode());
        assertEquals(HttpStatus.OK, page.getStatusCode());
        assertEquals(HttpStatus.OK, styles.getStatusCode());
        assertEquals(HttpStatus.OK, script.getStatusCode());
        assertNotNull(home.getBody());
        assertTrue(home.getBody().contains("pages/landingpage.html"));
    }

    @Test
    void authenticatedCheckoutUsesServerPricesAndAppearsInAccountHistory() {
        String email = "shopper-" + UUID.randomUUID() + "@example.test";
        ResponseEntity<Map> registration = http.postForEntity("/api/auth/register",
                Map.of("name", "Demo Shopper", "email", email, "password", "correct-horse-42"),
                Map.class);
        assertEquals(HttpStatus.OK, registration.getStatusCode());
        String cookie = cookieFrom(registration);

        Map<String, Object> checkout = Map.of(
                "name", "Demo Shopper",
                "email", email,
                "phone", "9876543210",
                "address", "42 Market Street",
                "city", "Pune",
                "postalCode", "411001",
                "items", List.of(Map.of("id", "headphones", "quantity", 1, "price", 1)));
        ResponseEntity<Map> placedOrder = http.exchange("/api/orders", HttpMethod.POST,
                new HttpEntity<>(checkout, cookieHeaders(cookie)), Map.class);

        assertEquals(HttpStatus.CREATED, placedOrder.getStatusCode());
        assertNotNull(placedOrder.getBody());
        assertEquals(8500, ((Number) placedOrder.getBody().get("subtotal")).longValue());
        assertEquals(0, ((Number) placedOrder.getBody().get("shipping")).longValue());
        assertEquals(8500, ((Number) placedOrder.getBody().get("total")).longValue());
        assertEquals("No payment collected", placedOrder.getBody().get("payment"));

        ResponseEntity<Map[]> history = http.exchange("/api/orders", HttpMethod.GET,
                new HttpEntity<>(cookieHeaders(cookie)), Map[].class);
        assertEquals(HttpStatus.OK, history.getStatusCode());
        assertNotNull(history.getBody());
        assertEquals(1, history.getBody().length);
        assertEquals(placedOrder.getBody().get("id"), history.getBody()[0].get("id"));
    }

    @Test
    void guestCheckoutAlsoPersistsOrdersWithoutRequiringPayment() {
        Map<String, Object> checkout = Map.of(
                "name", "Guest Shopper",
                "email", "guest-" + UUID.randomUUID() + "@example.test",
                "phone", "9876543210",
                "address", "5 Garden Road",
                "city", "Mumbai",
                "postalCode", "400001",
                "items", List.of(Map.of("id", "watch", "quantity", 1)));

        ResponseEntity<Map> placedOrder = http.postForEntity("/api/orders", checkout, Map.class);

        assertEquals(HttpStatus.CREATED, placedOrder.getStatusCode());
        assertNotNull(placedOrder.getBody());
        assertEquals(4200, ((Number) placedOrder.getBody().get("subtotal")).longValue());
        assertEquals(99, ((Number) placedOrder.getBody().get("shipping")).longValue());
        assertEquals(4299, ((Number) placedOrder.getBody().get("total")).longValue());
    }

    private static String cookieFrom(ResponseEntity<?> response) {
        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        return cookie.split(";", 2)[0];
    }

    private static HttpHeaders cookieHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
