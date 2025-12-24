#!/bin/bash

echo "Starting services with Docker Compose..."

# Build and start services
docker-compose up --build -d

echo ""
echo "Services starting in background..."
echo "- Inventory Service: http://localhost:8081"
echo "- Operational Service: http://localhost:8080"
echo "- UI Service: http://localhost:3000"
echo ""
echo "Commands:"
echo "  View logs: docker-compose logs -f [service-name]"
echo "  Stop services: docker-compose down"
echo "  View status: docker-compose ps"