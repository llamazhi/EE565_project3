import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.util.*;

public class LSPSender extends Thread {
    // private RemoteServerInfo serverInfo;
    // private int interval;
    private final static int bufferSize = 8192;
    private static int seqNum = 0;
    private final static int TTL = 10;

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
            LSPSender.seqNum += 1;
            RemoteServerInfo curr = VodServer.getHomeNodeInfo();
            String message = "LSPSeqNum=" + LSPSender.seqNum + " "
                    + "TTL=" + LSPSender.TTL + " "
                    + "senderName=" + curr.getName() + " "
                    + "senderInfo=" + curr.toPeerFormat() + " "
                    + "originName=" + curr.getName() + " "
                    + "originInfo=" + curr.toPeerFormat() + " ";
            ArrayList<RemoteServerInfo> neighbors = curr.getNeighbors();
            int peer_count = 0;
            for (RemoteServerInfo neighbor : neighbors) {
                peer_count++;
                message += "peer_" + peer_count + "=" + neighbor.toPeerFormat() + " ";
            }
            System.out.println(message);
            byte[] messageBytes = message.getBytes();
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
