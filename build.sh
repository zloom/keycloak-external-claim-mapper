#!/bin/bash
mvn dependency:copy -Dartifact=com.jayway.jsonpath:json-path:2.8.0 -DoutputDirectory=build
mvn clean package -Pconf -Ddir=../build