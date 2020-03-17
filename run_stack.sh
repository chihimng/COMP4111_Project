#!/bin/sh
set -e
./gradlew clean
./gradlew jar
docker-compose up --build
