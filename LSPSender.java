import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    // TODO: send LSP to all the nodes
    private RemoteServerInfo serverInfo;
    private int interval;
    private final static int bufferSize = 8192;

    public LSPSender(RemoteServerInfo info) {
        this.serverInfo = info;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send link state packe
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data);

            for (RemoteServerInfo udpserver : remoteServers) {
                String message = path + " " + udpserver.rate;
                byte[] messageBytes = message.getBytes();
                System.arraycopy(messageBytes, 0, requestData, 4, messageBytes.length);
                DatagramPacket outPkt = new DatagramPacket(requestData, requestData.length, udpserver.host,
                        udpserver.port);
                socket.send(outPkt);
            }
        }

    }
}
