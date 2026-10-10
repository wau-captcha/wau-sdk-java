package com.waucaptcha;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class BaseUrlTest {

    private static WauCaptchaClient build(String baseUrl) {
        return WauCaptchaClient.builder().baseUrl(baseUrl).secretKey("sk_test").build();
    }

    @Test
    void httpsIsRequired() {
        assertDoesNotThrow(() -> build("https://api.waucaptcha.com"));
        assertDoesNotThrow(() -> build(" https://api.waucaptcha.com/ "));
        for (String address : new String[] {"http://api.waucaptcha.com", "ftp://api.waucaptcha.com", "not a url", "javascript:alert(1)",
                "https://", ""}) {
            assertThrows(IllegalStateException.class, () -> build(address), address);
        }
    }

    @Test
    void plainHttpOnlyForLocalDevelopment() {
        assertDoesNotThrow(() -> build("http://localhost:8080"));
        assertDoesNotThrow(() -> build("http://127.0.0.1:8080"));
        assertDoesNotThrow(() -> build("http://[::1]:8080"));
        assertThrows(IllegalStateException.class, () -> build("http://192.168.1.10:8080"));
    }

    @Test
    void trailingSlashesAreDropped() throws IOException {
        try (StubService service = new StubService()) {
            service.reply("/siteverify", 200, "{\"success\":false}");

            build(service.baseUrl() + "//").verifyToken("token");

            assertEquals("/siteverify", service.last().path());
        }
    }
}
