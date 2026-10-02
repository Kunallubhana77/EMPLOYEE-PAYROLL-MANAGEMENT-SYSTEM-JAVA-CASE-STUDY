#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac -d out src/PayrollApp.java
exec java -cp out PayrollApp
