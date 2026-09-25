package com.dsm.mapstruct;

import com.dsm.mapstruct.adapter.api.ipc.IpcServerRunner;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A server nobody connects to must not live forever (the idle monitor only starts once a client
 * is connected). Uses a short accept timeout via the system property.
 */
class IpcServerNoClientTest {

    private static final String JAR_PATH = "target/mapstruct-path-explorer.jar";
    private static final long ACCEPT_TIMEOUT_MS = 500;

    private static Process startServer(Path socketPath) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-D" + IpcServerRunner.ACCEPT_TIMEOUT_PROPERTY + "=" + ACCEPT_TIMEOUT_MS,
                "-Dmapstruct.log.file=target/ipc-noclient-test.log",
                "-cp", JAR_PATH,
                "com.dsm.mapstruct.IpcServer",
                socketPath.toString()
        );
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    private static void waitForSocket(Path socketPath) throws InterruptedException {
        for (int i = 0; i < 100 && !Files.exists(socketPath); i++) {
            Thread.sleep(100);
        }
        assertThat(socketPath).as("server socket").exists();
    }

    private static Path freshSocketPath() {
        return Path.of("/tmp/test-mapstruct-noclient-" + System.nanoTime() + ".sock");
    }

    @Test
    void exitsAndRemovesSocketWhenNoClientConnectsInTime() throws Exception {
        assumeTrue(new File(JAR_PATH).exists(), "Skipping - JAR not built yet (run 'mvn package')");
        Path socketPath = freshSocketPath();
        Process server = startServer(socketPath);
        try {
            waitForSocket(socketPath);

            assertThat(server.waitFor(10, TimeUnit.SECONDS)).as("server exits without a client").isTrue();
            assertThat(server.exitValue()).isZero();
            assertThat(socketPath).doesNotExist();
        } finally {
            server.destroyForcibly();
            Files.deleteIfExists(socketPath);
        }
    }

    @Test
    void staysUpOnceAClientHasConnected() throws Exception {
        assumeTrue(new File(JAR_PATH).exists(), "Skipping - JAR not built yet (run 'mvn package')");
        Path socketPath = freshSocketPath();
        Process server = startServer(socketPath);
        try {
            waitForSocket(socketPath);

            try (SocketChannel client = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                connectWithRetry(client, socketPath);
                client.write(ByteBuffer.wrap("{\"id\":\"1\",\"method\":\"ping\",\"params\":{}}\n".getBytes(StandardCharsets.UTF_8)));
                ByteBuffer response = ByteBuffer.allocate(256);
                assertThat(client.read(response)).isGreaterThan(0);
                assertThat(new String(response.array(), 0, response.position(), StandardCharsets.UTF_8)).contains("pong");

                Thread.sleep(ACCEPT_TIMEOUT_MS * 3);
                assertThat(server.isAlive()).as("connected client keeps the server up").isTrue();
            }

            assertThat(server.waitFor(10, TimeUnit.SECONDS)).as("server exits when its client disconnects").isTrue();
        } finally {
            server.destroyForcibly();
            Files.deleteIfExists(socketPath);
        }
    }

    /**
     * The socket file appears on bind() and accepts connections after listen(); bridge the gap.
     */
    private static void connectWithRetry(SocketChannel client, Path socketPath) throws Exception {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        IOException last = null;
        for (int i = 0; i < 50; i++) {
            try {
                client.connect(address);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(20);
            }
        }
        throw last;
    }
}
