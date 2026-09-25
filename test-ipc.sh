#!/bin/bash
# Manual IPC smoke test: starts the server, drives it over ONE connection, then shuts it down.
#
# Requires `mvn package` (fat jar + compiled test classes). All requests share a single
# connection on purpose: the server serves one client and exits when that client disconnects,
# so a fresh `nc` per request would only ever get the first answer.

set -u

SOCKET_PATH="/tmp/test-mapstruct-ipc-$$.sock"
JAR_PATH="target/mapstruct-path-explorer.jar"
TEST_CLASSES="target/test-classes"
PERSON='com.dsm.mapstruct.testdata.TestClasses$Person'

if [ ! -f "$JAR_PATH" ] || [ ! -d "$TEST_CLASSES" ]; then
    echo "ERROR: build first: ./mvnw package -DskipTests"
    exit 1
fi

rm -f "$SOCKET_PATH"

echo "Starting IPC server..."
java -cp "$JAR_PATH:$TEST_CLASSES" com.dsm.mapstruct.IpcServer "$SOCKET_PATH" &
SERVER_PID=$!

for _ in $(seq 1 50); do
    [ -S "$SOCKET_PATH" ] && break
    sleep 0.2
done

if [ ! -S "$SOCKET_PATH" ]; then
    echo "ERROR: Socket not created at $SOCKET_PATH"
    kill "$SERVER_PID" 2>/dev/null
    exit 1
fi

echo "Server started (PID: $SERVER_PID), socket: $SOCKET_PATH"
echo

# Each request on its own line; the matching response lines are printed by nc.
{
    echo '# 1: ping'
    echo '{"id":"1","method":"ping","params":{}}'
    sleep 0.3
    echo '# 2: heartbeat'
    echo '{"id":"2","method":"heartbeat","params":{}}'
    sleep 0.3
    echo "# 3: explore_path - root of $PERSON"
    echo "{\"id\":\"3\",\"method\":\"explore_path\",\"params\":{\"sources\":[{\"name\":\"person\",\"type\":\"$PERSON\"}],\"pathExpression\":\"\",\"isEnum\":false}}"
    sleep 0.5
    echo '# 4: explore_path - person.address.'
    echo "{\"id\":\"4\",\"method\":\"explore_path\",\"params\":{\"sources\":[{\"name\":\"person\",\"type\":\"$PERSON\"}],\"pathExpression\":\"person.address.\",\"isEnum\":false}}"
    sleep 0.5
    echo '# 5: explore_path - unknown class (error response, connection stays open)'
    echo '{"id":"5","method":"explore_path","params":{"sources":[{"name":"fake","type":"com.nonexistent.FakeClass"}],"pathExpression":"","isEnum":false}}'
    sleep 0.5
    echo '# 6: explore_path - malformed sources entry (error response, connection stays open)'
    echo "{\"id\":\"6\",\"method\":\"explore_path\",\"params\":{\"sources\":[{\"type\":\"$PERSON\"}],\"pathExpression\":\"\",\"isEnum\":false}}"
    sleep 0.5
    echo "# 7: explore_type_source - $PERSON"
    echo "{\"id\":\"7\",\"method\":\"explore_type_source\",\"params\":{\"typeName\":\"$PERSON\"}}"
    sleep 0.5
    echo '# 8: shutdown'
    echo '{"id":"8","method":"shutdown","params":{}}'
    sleep 1
} | grep -v '^#' | nc -U "$SOCKET_PATH"

echo
echo "Waiting for server to exit..."
wait "$SERVER_PID"
echo "Server exit code: $?"
rm -f "$SOCKET_PATH"
echo "Test completed!"
