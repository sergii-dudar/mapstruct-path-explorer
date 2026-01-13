package com.dsm.mapstruct.adapter.api.ipc;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;

public class IpcClientMessageListener {
    public static void handleClient(SocketChannel client) {
        new Thread(() -> {
            try (
                 BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(client)));
                 BufferedWriter out = new BufferedWriter(new OutputStreamWriter(Channels.newOutputStream(client)))
            ) {
                String line;
                while ((line = in.readLine()) != null) {
                    // simple protocol
                    System.out.println("Received: " + line);

                    if (line.equals("ping")) {
                        out.write("pong\n");
                        out.flush();
                    } else {
                        out.write("unknown\n");
                        out.flush();
                    }
                }

                System.out.println("Client disconnected");
            } catch (IOException e) {
                System.out.println("Client error: " + e.getMessage());
            }
        }).start();
    }
}
