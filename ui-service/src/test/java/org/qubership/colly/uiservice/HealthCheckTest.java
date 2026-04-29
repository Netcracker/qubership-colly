package org.qubership.colly.uiservice;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class HealthCheckTest {

    @Test
    void liveness_probe_returns_up() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void readiness_probe_returns_up() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }
}
