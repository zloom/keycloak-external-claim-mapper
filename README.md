# Keycloak external claim mapper
Implementation of the keycloak internal SPI protocol-mapper, that allow to fetch remote http json data and include it into user JWT.
## Limitations
Built and tested with Keycloak 25.0.0, you can build and test with other versions on demand.
## Installation
To build Keycloak 25.0.0 image with extension from 0.0.1 release create dockerfile with following content:
```
FROM alpine:3.20 as build

RUN \
  wget https://github.com/zloom/keycloak-external-claim-mapper/releases/download/0.0.1/external.claim.mapper-0.0.1.tar.gz;\
  tar -zxvf external.claim.mapper-0.0.1.tar.gz;

FROM quay.io/keycloak/keycloak:25.0.0 as keycloak

COPY --from=build /build /opt/keycloak/providers

ENV KEYCLOAK_ADMIN="admin"
ENV KEYCLOAK_ADMIN_PASSWORD="admin"
```
Build and run it with following command:
```
docker build -t keycloak . && docker run -p 8080:8080 keycloak start-dev
```
Login and password is admin. Check `http://localhost:8080/admin/master/console/#/master/providers` find `external-claim-mapper` if extension is picked up by kecloak, it should be there.
## Configuration

