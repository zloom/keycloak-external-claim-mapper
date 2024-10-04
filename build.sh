#!/bin/bash
mvn dependency:copy -Dartifact=com.jayway.jsonpath:json-path:2.8.0 -DoutputDirectory=build
mvn clean package -Pconf -Ddir=../build
tar -C build -czvf build/external.claim.mapper-0.0.1.tar.gz .
