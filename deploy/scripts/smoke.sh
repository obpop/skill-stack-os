#!/bin/sh
set -eu

base_url=${1:?Usage: smoke.sh https://skill-stack.example.com}
base_url=${base_url%/}

curl --fail --silent --show-error --max-time 15 "$base_url/actuator/health/readiness" >/dev/null
curl --fail --silent --show-error --max-time 15 "$base_url/" >/dev/null
status=$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 15 "$base_url/api/v1/graph")
test "$status" = "401"
echo "Smoke checks passed for $base_url"
