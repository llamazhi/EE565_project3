import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    // private RemoteServerInfo serverInfo;
    // private int interval;
    private final static int bufferSize = 8192;

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send link state packet
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
            long currentTime = System.currentTimeMillis();
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
            String message = +currentTime + " " + lsp.toString();
            byte[] messageBytes = message.getBytes();
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
