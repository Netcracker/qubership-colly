package org.qubership.colly.db;

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
        migrateDatabase();
    }

    @Transactional
    public void migrateDatabase() {
        try {

            // Обновление CHECK constraint для EnvironmentType
            entityManager.createNativeQuery(
                "ALTER TABLE environments DROP CONSTRAINT IF EXISTS environments_type_check"
            ).executeUpdate();

            entityManager.createNativeQuery(
                "ALTER TABLE environments ADD CONSTRAINT environments_type_check " +
                "CHECK (type IN ('ENVIRONMENT', 'PORTAL', 'CSE_TOOLSET', 'DESIGN_TIME', 'APP_DEPLOYER', 'INFRASTRUCTURE', 'UNDEFINED'))"
            ).executeUpdate();

            System.out.println("✅ Successfully migrated database schema");
        } catch (Exception e) {
            System.out.println("Migration info: " + e.getMessage());
        }
    }
}
