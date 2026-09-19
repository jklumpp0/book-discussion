package com.octoberdiscussion.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(HealthController::class)
class HealthControllerTest {
    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `health route returns OK`() {
        webTestClient
            .get()
            .uri("/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody(String::class.java)
            .isEqualTo("OK")
    }
}
