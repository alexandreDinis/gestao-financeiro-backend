#!/bin/bash

echo "🚀 Iniciando o ambiente local do Gestão Financeiro (App Nativo + Banco Docker)..."

# Parar containers existentes
echo "🛑 Parando containers antigos..."
docker compose down

# Iniciar apenas o PostgreSQL via Docker
echo "🐳 Iniciando o banco de dados PostgreSQL..."
docker compose up -d postgres

# Aguardar o banco subir
echo "⏳ Aguardando o banco de dados (5s)..."
sleep 5

# Iniciar a aplicação Spring Boot localmente usando o profile dev
echo "☕ Iniciando o Spring Boot (profile: dev)..."
mvn spring-boot:run -Dspring-boot.run.profiles=dev
