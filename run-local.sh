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

if [ -f "./mvnw" ]; then
  MVN_CMD="./mvnw"
elif command -v mvn &> /dev/null; then
  MVN_CMD="mvn"
else
  echo "❌ Erro: Maven não encontrado! Instale o Maven ou gere o wrapper com: mvn -N wrapper:wrapper"
  exit 1
fi

$MVN_CMD spring-boot:run -Dspring-boot.run.profiles=dev
