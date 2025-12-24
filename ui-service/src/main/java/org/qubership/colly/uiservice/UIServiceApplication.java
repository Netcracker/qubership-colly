package org.qubership.colly.uiservice;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.logging.Log;

/**
 * UI Service - Gateway for frontend application
 * Aggregates data from Inventory and Operational services
 */
@QuarkusMain
public class UIServiceApplication implements QuarkusApplication {

    public static void main(String[] args) {
        Quarkus.run(UIServiceApplication.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        Log.info("UI Service started successfully");
        Quarkus.waitForExit();
        return 0;
    }
}
