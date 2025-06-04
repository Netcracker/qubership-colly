package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.qubership.colly.monitoring.MonitoringService;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class ClusterResourcesRestTest {

    @InjectMock
    MonitoringService monitoringService;

    @Test
    void load_environments_without_auth() {
        given()
                .when().get("/colly/environments")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_environments() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/environments")
                .then()
                .statusCode(200)
                .body("name", hasItem("env-test"));
    }


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void save_environment_with_auth() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);

        given()
                .formParam("owner", "test-owner")
                .formParam("description", "test-description")
                .formParam("status", "active")
                .formParam("labels", "label1,label2")
                .formParam("type", "development")
                .formParam("team", "test-team")
                .when().post("/colly/environments/1")
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test")
    void save_environment_without_admin_role() {

        given()
                .formParam("name", "test-env")
                .formParam("owner", "test-owner")
                .formParam("description", "test-description")
                .formParam("status", "active")
                .formParam("labels", "label1,label2")
                .formParam("type", "development")
                .formParam("team", "test-team")
                .when().post("/colly/environments/1")
                .then()
                .statusCode(403);
    }

}
