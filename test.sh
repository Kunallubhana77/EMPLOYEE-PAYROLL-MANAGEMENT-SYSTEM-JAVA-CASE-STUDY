#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/PayrollApp.java test/PayrollAppTest.java
java -Djava.awt.headless=true -cp out PayrollAppTest
