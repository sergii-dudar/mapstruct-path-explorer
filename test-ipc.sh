#!/bin/bash
# Test script for IPC server

SOCKET_PATH="/tmp/test-mapstruct-ipc.sock"
JAR_PATH="target/mapstruct-path-explorer.jar"

# Clean up old socket
rm -f "$SOCKET_PATH"

# Start server in background
echo "Starting IPC server..."
java -cp "$JAR_PATH" com.dsm.mapstruct.IpcServer "$SOCKET_PATH" &
SERVER_PID=$!

# Give server time to start
sleep 2

# Check if socket was created
if [ ! -S "$SOCKET_PATH" ]; then
    echo "ERROR: Socket not created at $SOCKET_PATH"
    kill $SERVER_PID 2>/dev/null
    exit 1
fi

echo "Server started successfully (PID: $SERVER_PID)"
echo "Socket created at: $SOCKET_PATH"

# Test 1: Ping
echo ""
echo "Test 1: Ping"
echo '{"id":"1","method":"ping","params":{}}' | nc -U "$SOCKET_PATH"

# Test 2: Heartbeat
echo ""
echo "Test 2: Heartbeat"
echo '{"id":"2","method":"heartbeat","params":{}}' | nc -U "$SOCKET_PATH"

# Test 3: Explore path (using a test class from the project)
echo ""
echo "Test 3: Explore Path - User.address"
echo '{"id":"3","method":"explore_path","params":{"className":"com.dsm.mapstruct.integration.domain.User","pathExpression":"address."}}' | nc -U "$SOCKET_PATH"

# Test 4: Explore path - nested field
echo ""
echo "Test 4: Explore Path - User (root level)"
echo '{"id":"4","method":"explore_path","params":{"className":"com.dsm.mapstruct.integration.domain.User","pathExpression":""}}' | nc -U "$SOCKET_PATH"

# Test 5: Invalid class
echo ""
echo "Test 5: Error handling - Invalid class"
echo '{"id":"5","method":"explore_path","params":{"className":"com.nonexistent.FakeClass","pathExpression":""}}' | nc -U "$SOCKET_PATH"

# Wait a bit for responses
sleep 1

# Shutdown server
echo ""
echo "Shutting down server..."
echo '{"id":"6","method":"shutdown","params":{}}' | nc -U "$SOCKET_PATH"

# Wait for server to exit
sleep 1

# Clean up
rm -f "$SOCKET_PATH"

echo ""
echo "Test completed!"
