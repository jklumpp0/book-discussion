package com.octoberdiscussion.health

import com.octoberdiscussion.auth.SessionRepository
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient

// SessionAuthWebFilter (auth package) is a @Component WebFilter, so @WebFluxTest picks it up
// even though this slice only targets HealthController; it needs a SessionRepository bean,
// which the slice otherwise has no DataSource to build for real, so we stub it here.
@WebFluxTest(HealthController::class)
@Import(HealthControllerTest.StubBeans::class)
class HealthControllerTest {
    @TestConfiguration
    class StubBeans {
        @Bean
        fun sessionRepository(): SessionRepository = mockk(relaxed = true)
    }

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
