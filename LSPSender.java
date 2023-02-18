import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    // private RemoteServerInfo serverInfo;
    // private int interval;
    private final static int bufferSize = 8192;

    // send HELLO to neighbors
    public static void hello() {
        // clear old active neighbors
        VodServer.activeNeighbors.clear();

        // say hello to all neighbors
        try (DatagramSocket socket = new DatagramSocket(0)) {
            // send hello link state packet
            byte[] data = new byte[bufferSize];
            VodServer.intToByteArray(-1, data); // seqnum = -1 for LSP
            RemoteServerInfo curr = VodServer.getHomeNodeInfo();
            String message = "HELLO AreYouAlive? " + curr.getName() + " " + curr.toPeerFormat();
            byte[] messageBytes = message.toString().getBytes();
            System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
            ArrayList<RemoteServerInfo> neighbors = curr.getNeighbors();
            for (RemoteServerInfo neighbor : neighbors) {
                DatagramPacket outPkt = new DatagramPacket(data, data.length, neighbor.getHost(),
                        neighbor.getBackendPort());
                socket.send(outPkt);
            }
        } catch (IOException ex) {
            System.out.println("SocketCannotOpenError");
        }
    }

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
            lsp.addProperty("senderUUID", curr.getUUID());
            lsp.addProperty("senderName", curr.getName());
            lsp.addProperty("timestamp", currentTime);
            lsp.add("neighbors", new JsonArray());
            for (RemoteServerInfo neighbor : neighbors) {
                JsonObject neighborJson = new JsonObject();
                neighborJson.addProperty("neighborUUID", neighbor.getUUID());
                neighborJson.addProperty("neighborName", neighbor.getName());
                neighborJson.addProperty("metric", neighbor.getMetric());
                lsp.getAsJsonArray("neighbors").add(neighborJson);
            }
            System.out.println(lsp);
            byte[] messageBytes = lsp.toString().getBytes();
            System.arraycopy(messageBytes, 0, data, 4, messageBytes.length);
            for (RemoteServerInfo neighbor : neighbors) {
                DatagramPacket outPkt = new DatagramPacket(data, data.length, neighbor.getHost(),
                        neighbor.getBackendPort());
                socket.send(outPkt);
            }
        } catch (IOException ex) {
            System.out.println("SocketCannotOpenError");
        }

    }
}
