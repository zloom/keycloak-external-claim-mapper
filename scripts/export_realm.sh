#!/bin/bash
docker rm kc_export
cd ..
docker build -t kc_export .
docker run -it --network keycloak-external-claim-mapper_backend --name kc_export kc_export export --dir /opt/keycloak/export --realm dev --users realm_file
docker cp kc_export:/opt/keycloak/export/dev-realm.json ./keycloak/export/dev-realm.json
docker rm kc_export

