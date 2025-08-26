-- Migration script to change deploymentVersion column type from VARCHAR(255) to TEXT
ALTER TABLE environments ALTER COLUMN deploymentVersion TYPE TEXT;
ALTER TABLE environments ALTER COLUMN description TYPE TEXT;
