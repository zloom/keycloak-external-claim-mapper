[![GitHub Release](https://img.shields.io/github/v/release/zloom/keycloak-external-claim-mapper?color=blue)](https://github.com/zloom/keycloak-external-claim-mapper/releases)
[![GitHub license](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/zloom/keycloak-external-claim-mapper/blob/main/LICENSE)
# Keycloak external claim mapper
Implementation of the keycloak internal SPI protocol-mapper, that allows to fetch remote http json data and include it into user JWT.
## Limitations
Built and tested with Keycloak 25.0.0, maven 3.6.3, openjdk21, you can build and test with other versions if required.
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
Login and password are `admin`. Check `http://localhost:8080/admin/master/console/#/master/providers` and find `external-claim-mapper` if extension is picked up by kecloak, it should be there.
## Configuration
There are 3 fields to configure, except standard protocol mapper provider configuration.
### Remote url
This is remote url to fetch json from with jwt auth token, you can optionally use a placeholder to insert keycloak user id. 
- Extension will send get request with following headers:
`Content-Type: application/json`
`Authorization: Bearer eyJhb...28qdQ`
Token value will be your user keycloak JWT, without mapper data yet.
- Placeholder will work as following: assume your remote urls is `http://localhost/user/profile` so that `http://localhost/user/**userId**/profile` will be transformed to `http://localhost/user/063943d2-d7ed-4bca-812b-506518c38228/profile` given that `063943d2-d7ed-4bca-812b-506518c38228` is keycloak user id.
### Json path
Optional json path expression to transform your remote endpoint response data.
Given that **Token Claim Name** is configured as `user_roles` and remote endpoint response is:
```
{
  "roles": {
    "values": [
      "role1",
      "role2",
      "role3"
    ]
  }
}
```
In final jwt it will look as following:
```
...
"user_roles": {
  "roles": {
    "values": [
      "role1",
      "role2",
      "role3"
    ]
  }
}
...
```
You can set **Json path** to `$.roles.values` it will result to:
```
...
"user_roles": [
  "role1",
  "role2",
  "role3"
]
...
```
It is convenient when you don't have control over remote endpoint response shape, you can test json path expressions with https://jsonpath.com
### Error propagation
A switch to propagate errors that may occurs during data fetch and transformation. Mapper will fail for result code not from 2XX or 3XX codes. This flag also will propagate error on jsonpath transformation. Error will be propagated to keycloak token endpoint that will cause 500 error on auth. Exact error should be visible in keycloak logs.
## License
This project is licensed under the MIT License

