#!/bin/bash
cd ..
VERSION=0.0.2 
mvn versions:set -DnewVersion=$VERSION
mvn versions:commit
mvn dependency:copy -Dartifact=com.jayway.jsonpath:json-path:2.8.0 -DoutputDirectory=build
mvn clean package -Pconf -Ddir=../build
tar -C build -czvf build/external.claim.mapper-${VERSION}.tar.gz .
