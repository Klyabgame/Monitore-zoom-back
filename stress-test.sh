#!/usr/bin/env bash

# =========================================================================================
# Stress Test Script for Zoom Webhook Backpressure Buffer
# Simulates 45 concurrent student joins (meeting.participant_joined) in a single second.
# Automatically calculates the HMAC-SHA256 signature for each payload.
# =========================================================================================

# Configuration
ENDPOINT="http://localhost:8080/api/webhooks/zoom"
SECRET_TOKEN="DhrimNbTQO6I5uGY_J9ojA" # Matches application.properties default
NUM_REQUESTS=45

echo "======================================================================"
echo "Starting Stress Test: Sending $NUM_REQUESTS concurrent requests..."
echo "Target Endpoint: $ENDPOINT"
echo "======================================================================"

# Target timestamp (same for all concurrent requests)
TIMESTAMP=$(date +%s)
MEETING_ID="1234567890"
TOPIC="Clase Reactiva de Alta Performance"

for i in $(seq 1 $NUM_REQUESTS); do
  # Construct payload
  PAYLOAD="{\"event\":\"meeting.participant_joined\",\"event_ts\":$TIMESTAMP,\"payload\":{\"account_id\":\"acc_stress_test\",\"object\":{\"id\":\"$MEETING_ID\",\"topic\":\"$TOPIC\",\"participant\":{\"user_id\":\"stress_$i\",\"user_name\":\"Alumno Virtual $i\",\"email\":\"alumno$i@test.com\"}}}}"

  # Construct signature message v0:timestamp:payload
  MSG="v0:$TIMESTAMP:$PAYLOAD"

  # Calculate HMAC-SHA256 signature
  # Compatible with Linux, macOS, and Git Bash on Windows
  HEX_SIG=$(echo -n "$MSG" | openssl dgst -sha256 -hmac "$SECRET_TOKEN" | awk '{print $NF}')
  SIGNATURE="v0=$HEX_SIG"

  # Fire curl request in the background
  curl -s -o /dev/null -w "Request #$i: Status %{http_code}\n" -X POST "$ENDPOINT" \
    -H "Content-Type: application/json" \
    -H "x-zm-signature: $SIGNATURE" \
    -H "x-zm-request-timestamp: $TIMESTAMP" \
    -d "$PAYLOAD" &
done

# Wait for all background curl requests to complete
echo "--> All requests fired. Waiting for server acknowledgements..."
wait
echo "======================================================================"
echo "Stress test finished!"
echo "Check your Spring Boot console for batch processing logs."
echo "Check your Google Sheets to verify insertion in batches of 20."
echo "======================================================================"
