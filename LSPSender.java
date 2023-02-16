import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    // private RemoteServerInfo serverInfo;
    // private int interval;
    private final static int bufferSize = 8192;

    public static void longToByteArray(long value, byte[] buffer, int offset) {
        for (int i = 7; i >= 0; i--) {
            buffer[offset + i] = (byte) (value & 0xff); // bitwise AND to get the least significant byte
            value >>= 8; // shift the number to the right by 8 bits
        }
    }

    public static long byteArrayToLong(byte[] buffer, int offset) {
        long number = 0L;
        for (int i = 0; i < 8; i++) {
            number <<= 8; // shift the number to the left by 8 bits
            number |= (buffer[offset + i] & 0xff); // bitwise OR to combine the next byte with the number
        }
        return number;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send link state packet
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
            long currentTime = System.currentTimeMillis();
            longToByteArray(currentTime, data, 4);
            RemoteServerInfo curr = VodServer.getHomeNodeInfo();
            ArrayList<RemoteServerInfo> neighbors = curr.getNeighbors();
            JsonObject lsp = new JsonObject();
            lsp.add(curr.getUUID(), new JsonArray());
            for (RemoteServerInfo neighbor : neighbors) {
                JsonObject neighborJson = new JsonObject();
                neighborJson.addProperty("uuid", neighbor.getUUID());
                neighborJson.addProperty("name", neighbor.getName());
                neighborJson.addProperty("metric", neighbor.getMetric());
                lsp.getAsJsonArray(curr.getUUID()).add(neighborJson);
            }
            System.out.println(lsp);
            byte[] messageBytes = lsp.toString().getBytes();
            System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
            for (RemoteServerInfo neighbor : neighbors) {
                DatagramPacket outPkt = new DatagramPacket(data, data.length, neighbor.host,
                        neighbor.getBackendPort());
                socket.send(outPkt);
            }
        } catch (IOException ex) {
            System.out.println("SocketCannotOpenError");
        }

    }
}
