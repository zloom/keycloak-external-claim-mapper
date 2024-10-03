ARG VERSION=25.0.0

FROM maven:3.9.1 AS build

RUN mvn dependency:copy -Dartifact=com.jayway.jsonpath:json-path:2.8.0 -DoutputDirectory=/deps

COPY src pom.xml /src/

RUN mvn clean package -f /src/pom.xml

FROM quay.io/keycloak/keycloak:$VERSION as debug

COPY --from=build /deps /opt/keycloak/providers
COPY --from=build /src/target/. /opt/keycloak/providers

RUN /opt/keycloak/bin/kc.sh build

ENV KEYCLOAK_ADMIN="admin"
ENV KEYCLOAK_ADMIN_PASSWORD="admin"
ENV DEBUG='true'
ENV DEBUG_SUSPEND='n'