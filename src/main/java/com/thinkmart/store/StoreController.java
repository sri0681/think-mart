package com.thinkmart.store;

import java.time.Duration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class StoreController {
    private static final String SESSION_COOKIE = "thinkmart_session";
    private static final Duration SESSION_DURATION = Duration.ofDays(30);
    private final StoreService store;

    public StoreController(StoreService store) {
        this.store = store;
    }

    public record AuthRequest(String name, String email, String password) {}

    public record ContactRequest(String name, String email, String message) {}

    public record ApiMessage(String message) {}

    @GetMapping("/products")
    public List<StoreService.Product> products() {
        return store.products();
    }

    @PostMapping("/auth/register")
    public ResponseEntity<StoreService.Customer> register(
            @RequestBody AuthRequest request, HttpServletRequest servletRequest) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account details are required.");
        String token = store.register(request.name(), request.email(), request.password());
        StoreService.Customer customer = store.currentCustomer(token);
        return ResponseEntity.ok().header("Set-Cookie", sessionCookie(token, servletRequest).toString()).body(customer);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<StoreService.Customer> login(
            @RequestBody AuthRequest request, HttpServletRequest servletRequest) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sign-in details are required.");
        String token = store.login(request.email(), request.password());
        StoreService.Customer customer = store.currentCustomer(token);
        return ResponseEntity.ok().header("Set-Cookie", sessionCookie(token, servletRequest).toString()).body(customer);
    }

    @GetMapping("/auth/me")
    public StoreService.Customer currentCustomer(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return store.currentCustomer(token);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiMessage> logout(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            HttpServletRequest servletRequest) {
        store.logout(token);
        ResponseCookie expired = ResponseCookie.from(SESSION_COOKIE, "").httpOnly(true)
                .secure(servletRequest.isSecure()).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        return ResponseEntity.ok().header("Set-Cookie", expired.toString()).body(new ApiMessage("Signed out."));
    }

    @PostMapping("/orders")
    public ResponseEntity<StoreService.Order> placeOrder(
            @RequestBody StoreService.CheckoutRequest request,
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(store.placeOrder(request, store.optionalCustomer(token)));
    }

    @GetMapping("/orders")
    public List<StoreService.Order> orders(
            @CookieValue(name = SESSION_COOKIE, required = false) String token) {
        return store.ordersFor(store.currentCustomer(token));
    }

    @PostMapping("/contact")
    public ResponseEntity<ApiMessage> contact(@RequestBody ContactRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message details are required.");
        store.saveMessage(request.name(), request.email(), request.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiMessage("Your message has been saved."));
    }

    private ResponseCookie sessionCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(SESSION_COOKIE, token).httpOnly(true)
                .secure(request.isSecure()).sameSite("Lax").path("/")
                .maxAge(SESSION_DURATION).build();
    }
}
