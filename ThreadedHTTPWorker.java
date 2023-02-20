import java.net.*;
import java.io.*;
import java.lang.Thread;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Map;
import java.util.Collections;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

// ThreadedHTTPWorker class is responsible for all the
// actual string & data transfer
public class ThreadedHTTPWorker extends Thread {
    private Socket client;
    private DataInputStream inputStream = null;
    private DataOutputStream outputStream = null;
    private final String CRLF = "\r\n";
    private int[] rangeNum;

    public ThreadedHTTPWorker(Socket client) {
        this.client = client;
    }

    @Override
    public void run() {
        try {
            System.out.println("Worker Thread starts running ... ");
            this.outputStream = new DataOutputStream(this.client.getOutputStream());
            this.inputStream = new DataInputStream(this.client.getInputStream());

            // retrieve request header as String
            BufferedReader in = new BufferedReader(new InputStreamReader(this.client.getInputStream()));
            String inputLine;
            String req = "";
            while ((inputLine = in.readLine()) != null) {
                req += inputLine;
                req += "\r\n";
                if (inputLine.length() == 0) {
                    break;
                }
            }
            String relativeURL = preprocessReq(req);
            parseURI(req, relativeURL);
        } catch (IOException e) {
            System.out.println("Something wrong with connection");
            e.printStackTrace();
        } finally {
            if (this.outputStream != null) {
                try {
                    this.outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (this.inputStream != null) {
                try {
                    this.inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (this.client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String preprocessReq(String req) {
        int index = req.indexOf("GET");
        int nextIndex = req.indexOf("HTTP/");
        if (index != -1 && nextIndex != -1) {
            String relativeURL = req.substring(index + 4, nextIndex - 1).trim();
            System.out.println("relativeURL: " + "\"" + relativeURL + "\"");
            return relativeURL;
        } else {
            return "";
        }
    }

    private void parseURI(String req, String relativeURL) {
        HTTPURIParser parser = new HTTPURIParser(relativeURL);

        if (!parser.hasUDPRequest()) {
            // This is a local request
            String path = parser.getPath();
            if (path.equals("/")) {
                sendErrorResponse("This file is not found in local server.");
                return;
            }
            String MIMEType = categorizeFile(path);
            File f = new File(path);
            if (f.exists()) {
                if (req.contains("Range: ")) {
                    setRange(req);
                    sendPartialContent(MIMEType, this.rangeNum, f);
                } else {
                    sendFullContent(MIMEType, f);
                }
            } else {
                sendErrorResponse("This file is not found in local server.");
            }
        } else if (parser.hasAdd()) {
            // store the parameter information
            String[] queries = parser.getQueries();
            addPeer(queries);
        } else if (parser.hasView()) {
            String path = parser.getPath();
            path = path.replace("/peer/view/", "");
            viewContent(path);
        } else if (parser.hasConfig()) {
            configureRate(parser);
        } else if (parser.hasStatus()) {
            getStatus();
        } else if (parser.hasKill()) {
            System.exit(0);
        } else if (parser.hasUUID()) {
            showUUID();
        } else if (parser.hasNeighbors()) {
            showNeighbors();
        } else if (parser.hasAddNeighbor()) {
            String[] queries = parser.getQueries();
            addNeighbor(queries);
        } else if (parser.hasMap()) {
            showNeighborMap();
        } else if (parser.hasRank()) {
            String path = parser.getPath();
            path = path.replace("/peer/rank/", "");
            showContentRank(path);
        } else {
            sendErrorResponse("Invalid request");
        }
    }

    private void showUUID() {
        try {
            NodeInfo info = VodServer.getHomeNodeInfo();
            JsonObject uuid = new JsonObject();
            uuid.addProperty("uuid", info.getUUID());
            String uuidStr = uuid.toString();
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: application/json" + this.CRLF +
                    "Content-Length:" + uuidStr.length() + this.CRLF +
                    this.CRLF + uuid;
            // System.out.println(uuid.toString());
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // A modified version of addPeer
    private void addNeighbor(String[] queries) {
        try {
            // System.out.println("addNeighbor reached");
            HashMap<String, String> keyValue = new HashMap<>();
            for (String q : queries) {
                String[] queryComponents = q.split("=");
                keyValue.put(queryComponents[0], queryComponents[1]);
            }
            System.out.println(keyValue);

            NodeInfo neighbor = NodeInfo.parseNeighbor(keyValue);
            VodServer.setNeighbor(neighbor);
            System.out.println("neighbors after add: " + VodServer.getNeighbors());

            // Pass the queries to backend port
            // At this stage, we just print them out
            String html = "<html><body><h1>Neighbor Added!</h1></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);

        } catch (NumberFormatException | IOException e) {
            sendErrorResponse("invalid query");
            e.printStackTrace();
        }
    }

    private void checkNeighborsActive() {
        LSPSender.hello();
        Boolean neighborDead = false;
        for (Map.Entry<String, Integer> entry : VodServer.neighborNoResponseCount.entrySet()) {
            String uuid = entry.getKey();
            Integer count = entry.getValue();
            if (count >= 3) {
                if (VodServer.activeNeighbors.remove(uuid) != null) {
                    neighborDead = true;
                }
            }
        }
        if (neighborDead) {
            VodServer.LSPSeqNum++;
            System.out.println("Some neighbors become inactive! Increase LSPSeqNum.");
            System.out.println("LSPSeqNum = " + VodServer.LSPSeqNum);
        }
    }

    private void showNeighbors() {
        checkNeighborsActive();
        try {
            JsonArray jsonArray = new JsonArray();
            for (Map.Entry<String, NodeInfo> entry : VodServer.activeNeighbors.entrySet()) {
                NodeInfo neighborInfo = entry.getValue();
                JsonObject node = new JsonObject();
                node.addProperty("uuid", neighborInfo.getUUID());
                node.addProperty("name", neighborInfo.getName());
                node.addProperty("host", neighborInfo.getHostname());
                node.addProperty("frontend", neighborInfo.getFrontendPort());
                node.addProperty("backend", neighborInfo.getBackendPort());
                node.addProperty("metric", neighborInfo.getMetric());
                jsonArray.add(node);
            }
            String jsonStr = jsonArray.toString();
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: application/json" + this.CRLF +
                    "Content-Length:" + jsonStr.length() + this.CRLF +
                    this.CRLF + jsonArray;
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showNeighborMap() {
        // checkNeighborsActive();
        try {
            HashMap<String, HashMap<String, NodeInfo>> adjMap = VodServer.getAdjMap();
            JsonObject homeNodeObj = new JsonObject();
            for (String uuid : adjMap.keySet()) {
                String homeNodeName = "";
                if (!VodServer.uuidToInfo.containsKey(uuid)) {
                    homeNodeName = uuid;
                } else {
                    homeNodeName = VodServer.uuidToInfo.get(uuid).getName();
                }

                HashMap<String, NodeInfo> neighbors = adjMap.get(uuid);
                JsonObject nodeObj = new JsonObject();
                Long currentTime = System.currentTimeMillis();
                for (String neighborUUID : neighbors.keySet()) {
                    NodeInfo neighborInfo = neighbors.get(neighborUUID);
                    // check TTL
                    if (currentTime - neighborInfo.getTimestamp() >= 1000 * 10) {
                        // information outdated
                        continue;
                    }

                    String neighborName = neighborInfo.getName();
                    double metric = neighborInfo.getMetric();
                    nodeObj.addProperty(neighborName, metric);
                }
                homeNodeObj.add(homeNodeName, nodeObj);
            }
            String jsonStr = homeNodeObj.toString();
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: application/json" + this.CRLF +
                    "Content-Length:" + jsonStr.length() + this.CRLF +
                    this.CRLF + homeNodeObj;
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // For example,
    // [{“node2”:10}, {“node3”:20}, {“node4”:50}]
    private void showContentRank(String filePath) {
        try {
            // use a hashset to record all the nodes with specified content
            TreeMap<Double, NodeInfo> nodesWithContent = new TreeMap<>();

            // check the neighbor nodes of the curr node
            for (String uuid : VodServer.distanceFromOrigin.keySet()) {
                NodeInfo node = VodServer.uuidToInfo.get(uuid);
                if (node.getContentDir().equals(filePath)) { // TODO: modify to certain filepath
                    nodesWithContent.put(VodServer.distanceFromOrigin.get(uuid), node);
                }
            }
            JsonArray jsonArray = new JsonArray();
            // add to the jsonArray according to the order of distance
            for (Map.Entry<Double, NodeInfo> entry : nodesWithContent.entrySet()) {
                if (entry.getValue().getUUID().equals(VodServer.getHomeNodeInfo().getUUID())) {
                    continue;
                }
                Double distance = entry.getKey();
                String name = entry.getValue().getName();
                JsonObject nodeInfo = new JsonObject();
                nodeInfo.addProperty(name, distance);
                jsonArray.add(nodeInfo);
            }

            String jsonStr = jsonArray.toString();
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: application/json" + this.CRLF +
                    "Content-Length:" + jsonStr.length() + this.CRLF +
                    this.CRLF + jsonArray;
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // store the parameter information
    private void addPeer(String[] queries) {
        try {
            // System.out.println("addPeer reached");
            HashMap<String, String> keyValue = new HashMap<>();
            for (String q : queries) {
                String[] queryComponents = q.split("=");
                keyValue.put(queryComponents[0], queryComponents[1]);
            }
            System.out.println(keyValue);

            // may pass the parameters to UDP later
            String path = keyValue.get("path");
            int port = Integer.parseInt(keyValue.get("port"));
            String host = keyValue.get("host");
            int rate = Integer.parseInt(keyValue.get("rate"));
            NodeInfo info = new NodeInfo(host, port, rate);
            VodServer.addPeer(path, info);
            // Pass the queries to backend port
            // At this stage, we just print them out
            String html = "<html><body><h1>Peer Added!</h1></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);

        } catch (NumberFormatException | IOException e) {
            sendErrorResponse("invalid query");
            e.printStackTrace();
        }
    }

    private void viewContent(String path) {
        UDPClient udpclient = new UDPClient();

        System.out.println(path);
        ArrayList<NodeInfo> infos = VodServer.getRemoteServerInfo(path);
        if (infos == null) {
            sendErrorResponse("Please add peer first!");
            return;
        }
        // TODO: get content from the right server(s)
        String result = udpclient.startClient(path, infos, this.outputStream);
        if (!result.equals("Success")) {
            sendErrorResponse(result);
        }
    }

    private void sendErrorResponse(String msg) {
        try {
            String html = "<html><body><h1>404 Not Found!</h1><p>" + msg + "</p></body></html>";
            String response = "HTTP/1.1 404 Not Found" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
            this.outputStream.writeBytes(html);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void configureRate(HTTPURIParser parser) {
        try {
            int rate = Integer.parseInt(parser.getQueries()[0].split("=")[1]);
            VodServer.setBitRate(rate);
            String html = "<html><body><h1>Client receiving bit rate set to " + rate
                    + " kbps</h1></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
        } catch (NumberFormatException | IOException e) {
            sendErrorResponse("Invalid rate configure");
        }
    }

    private double getAverageBitRateWithinInterval(long interval) {
        long currentTime = System.currentTimeMillis();
        int index = Collections.binarySearch(VodServer.clientReceiveTimestamps, currentTime - interval);
        long startTime;
        if (index < 0) {
            index = -index - 1;
        }
        if (index == VodServer.clientReceiveTimestamps.size()) {
            return 0;
        } else {
            startTime = VodServer.clientReceiveTimestamps.get(index);
            return ((double) VodServer.clientReceiveTimestamps.size() - index) * 8 * (double) VodServer.bufferSize
                    / (double) (currentTime - startTime);
        }
    }

    private void getStatus() {
        try {
            String html = "<html><body><h1>Current status: </h1><p>File Complenteness: " + VodServer.getCompleteness()
                    + " %"
                    + "<br>Average bit rate in 1 second: " + getAverageBitRateWithinInterval(1000) + " kbps"
                    + "<br>Average bit rate in 10 second: " + getAverageBitRateWithinInterval(10000) + " kbps"
                    + "<br>Average bit rate in 60 second: " + getAverageBitRateWithinInterval(60000) + " kbps"
                    + "</p></body></html>";
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Type: text/html" + this.CRLF +
                    "Content-Length:" + html.getBytes().length + this.CRLF +
                    this.CRLF + html;
            this.outputStream.writeBytes(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // extract range from request
    private void setRange(String req) {
        String[] lines = req.split("\r\n");
        int start = 0;
        int end = 0;
        this.rangeNum = new int[] { 0, 0 };
        for (String l : lines) {
            // check if the line contains "Range: " field
            if (l.contains("Range: bytes=")) {
                int len = "Range: bytes=".length();
                String range = l.substring(len);
                String startNum = range.split("-")[0];
                String endNum = range.split("-")[1];
                start = Integer.parseInt(startNum);
                end = Integer.parseInt(endNum);
                this.rangeNum[0] = start;
                this.rangeNum[1] = end;
            }
        }
    }

    private String categorizeFile(String path) {
        try {
            // convert the file name into string
            String MIMEType = "";
            Path p = Paths.get(path);
            MIMEType = Files.probeContentType(p);
            return MIMEType;
        } catch (IOException e) {
            e.printStackTrace();
            return "Unacceptable file found";
        }
    }

    private void sendPartialContent(String MIMEType, int[] numRange, File f) {
        try {
            int rangeEnd = numRange[1];
            int rangeStart = numRange[0];
            int actualLength = rangeEnd - rangeStart + 1;
            String partialResponse = "HTTP/1.1 206 Partial Content" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + actualLength + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + f.length() + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(partialResponse);
            FileInputStream fileInputStream = new FileInputStream(f);
            byte[] buffer = new byte[rangeEnd];
            fileInputStream.read(buffer, rangeStart, rangeEnd);
            this.outputStream.write(buffer, 0, rangeEnd);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendFullContent(String MIMEType, File f) {
        try {
            String response = "HTTP/1.1 200 OK" + this.CRLF +
                    "Content-Type: " + MIMEType + this.CRLF +
                    "Content-Length: " + f.length() + this.CRLF +
                    "Date: " + getGMTDate(new Date()) + this.CRLF +
                    "Last-Modified: " + getGMTDate(f.lastModified()) + this.CRLF +
                    "Connection: close" + this.CRLF +
                    this.CRLF;
            this.outputStream.writeBytes(response);
            System.out.println("Response header sent ... ");
            int bytes = 0;
            // Open the File
            FileInputStream fileInputStream = new FileInputStream(f);

            // Here we break file into chunks
            byte[] buffer = new byte[1024];
            while ((bytes = fileInputStream.read(buffer)) != -1) {
                // Send the file
                this.outputStream.write(buffer, 0, bytes); // file content
                this.outputStream.flush(); // flush all the contents into stream
            }
            // close the file here
            fileInputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getGMTDate(Object date) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(date);
    }

}
