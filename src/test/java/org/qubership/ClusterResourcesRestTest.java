package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;
import org.qubership.colly.monitoring.MonitoringService;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ClusterResourcesRestTest {

    @InjectMock
    MonitoringService monitoringService;

    @Test
    @TestSecurity(user = "test")
    void testHelloEndpoint() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);
//                .body(containsString("demo-k8s"))
    }

}
