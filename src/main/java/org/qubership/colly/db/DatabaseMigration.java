package org.qubership.colly.db;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DatabaseMigration {

    @Inject
    EntityManager entityManager;

    void onStart(@Observes StartupEvent ev) {
        migrateDeploymentVersionColumn();
    }

    @Transactional
    public void migrateDeploymentVersionColumn() {
        Log.info("start migration");
        try {
            entityManager.createNativeQuery(
                "ALTER TABLE environments ALTER COLUMN deploymentVersion TYPE TEXT"
            ).executeUpdate();

            entityManager.createNativeQuery(
                "ALTER TABLE environments ALTER COLUMN description TYPE TEXT"
            ).executeUpdate();

            Log.info("✅ Successfully migrated columns to TEXT");
        } catch (Exception e) {
            Log.error("Migration info: " + e.getMessage());
        }
    }
}
