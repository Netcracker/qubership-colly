package org.qubership;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ClusterResourcesRestTest {

    @Test
    void testHelloEndpoint() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);
//                .body(containsString("demo-k8s"))
    }

}