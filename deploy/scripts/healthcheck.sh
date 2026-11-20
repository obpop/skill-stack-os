#!/bin/sh
set -eu

base_url=${SKILL_STACK_BASE_URL:?Set SKILL_STACK_BASE_URL}
curl --fail --silent --show-error --max-time 15 "${base_url%/}/actuator/health/readiness" >/dev/null
