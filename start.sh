#!/bin/bash
set -a; source .env; set +a
java -jar target/DukaanLocker-0.0.1-SNAPSHOT.jar --server.port=8081
