#!/bin/bash

echo "Building Docker images..."

# Build inventory service
echo "Building envgene-inventory-service..."
cd envgene-inventory-service
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    docker build -t envgene-inventory-service:latest .
    echo "✓ envgene-inventory-service image built successfully"
else
    echo "✗ Failed to build envgene-inventory-service"
    exit 1
fi
cd ..

# Build operational service
echo "Building environment-operational-service..."
cd environment-operational-service
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    docker build -t environment-operational-service:latest .
    echo "✓ environment-operational-service image built successfully"
else
    echo "✗ Failed to build environment-operational-service"
    exit 1
fi
cd ..

# Build ui service
echo "Building ui-service..."
cd ui-service
mvn clean package -DskipTests
if [ $? -eq 0 ]; then
    docker build -t ui-service:latest .
    echo "✓ ui-service image built successfully"
else
    echo "✗ Failed to build ui-service"
    exit 1
fi
cd ..

echo ""
echo "All Docker images built successfully:"
echo "- envgene-inventory-service:latest"
echo "- environment-operational-service:latest"
echo "- ui-service:latest"
