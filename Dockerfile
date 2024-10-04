FROM alpine:3.20 as build

RUN apk --update --no-cache add openjdk21 maven

RUN mvn dependency:copy -Dartifact=com.jayway.jsonpath:json-path:2.8.0 -DoutputDirectory=/deps

COPY ./external-claim-mapper/src /src/external-claim-mapper/src
COPY ./external-claim-mapper/pom.xml /src/external-claim-mapper/pom.xml
COPY ./pom.xml /src/pom.xml

RUN mvn clean package -f /src/pom.xml

FROM quay.io/keycloak/keycloak:25.0.0 as debug

COPY --from=build /deps /opt/keycloak/providers
COPY --from=build /src/external-claim-mapper/target/. /opt/keycloak/providers

RUN /opt/keycloak/bin/kc.sh build

ENV KEYCLOAK_ADMIN="admin"
ENV KEYCLOAK_ADMIN_PASSWORD="admin"
ENV DEBUG='true'
ENV DEBUG_SUSPEND='n'
ENV DEBUG_PORT='*:5005'
ENV KC_STORAGE=h2