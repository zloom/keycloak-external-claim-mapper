#!/bin/bash
curl 'http://localhost:8080/realms/dev/protocol/openid-connect/token'  \
-H 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=account' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=test' \
--data-urlencode 'password=test' | json_pp