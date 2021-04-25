#!/bin/bash

BOOTSTRAP_SQL="
create database hephaestus;
create user hephaestus with encrypted password 'hephaestus';
grant all privileges on database hephaestus to hephaestus;
"

# Start postgres
echo "Starting Postgres..."
docker pull postgres:latest
POSTGRES_SHA=$(
  docker run \
    --env POSTGRES_USER=postgres \
    --env POSTGRES_PASSWORD=postgres \
    --detach \
    --restart unless-stopped \
    -p 5432:5432 \
    -d postgres:latest
)

sleep 5
echo -e "Postgres running.\n"

# Initialize the DB
echo "Setting up the testing database..."
echo $BOOTSTRAP_SQL | docker exec -i $POSTGRES_SHA psql -U postgres
echo -e "\nDone!"