package bgu.spl.net.impl.stomp;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StompTestHarness {

    // ---------- Entry ----------
    public static void main(String[] args) throws Exception {
        String host = args.length >= 1 ? args[0] : "localhost";
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : 8888;

        System.out.println("== STOMP Test Harness ==");
        System.out.println("Target: " + host + ":" + port);

        StompClient alice = new StompClient("Alice", host, port);
        StompClient bob   = new StompClient("Bob", host, port);

        // Start sockets + reader threads
        alice.open();
        bob.open();

        // 1) CONNECT both
        Frame aConn = alice.connect("alice", "123", "1.2");
        requireCommand(aConn, new HashSet<>(Arrays.asList("CONNECTED", "ERROR")), "Alice CONNECT must get CONNECTED/ERROR");
        if (aConn.command.equals("ERROR")) {
            die("Alice CONNECT returned ERROR: " + aConn.body + " headers=" + aConn.headers);
        }

        Frame bConn = bob.connect("bob", "123", "1.2");
        requireCommand(bConn, new HashSet<>(Arrays.asList("CONNECTED", "ERROR")), "Bob CONNECT must get CONNECTED/ERROR");
        if (bConn.command.equals("ERROR")) {
            die("Bob CONNECT returned ERROR: " + bConn.body + " headers=" + bConn.headers);
        }

        // 2) SUBSCRIBE both to same topic
        String topic = "/topic/test";
        String aSubId = "0";
        String bSubId = "0";

        // If your server supports RECEIPT, this will verify it. If not, it won't fail.
        alice.subscribe(topic, aSubId, true);
        bob.subscribe(topic, bSubId, true);

        // 3) SEND from Bob -> expect MESSAGE at Alice (and probably Bob too, depending on your broadcast logic)
        String payload = "hello-from-bob-" + System.currentTimeMillis();
        bob.send(topic, payload, true);

        Frame aMsg = alice.waitForCommand("MESSAGE", 1500);
        if (aMsg == null) {
            die("Alice did NOT receive MESSAGE after Bob SEND. Check broadcast/subscriptions.");
        }
        System.out.println("[OK] Alice got MESSAGE body=" + aMsg.body);

        // Optional: validate destination header
        String dest = aMsg.headers.getOrDefault("destination", "");
        if (!dest.isEmpty() && !dest.equals(topic)) {
            die("Alice MESSAGE destination mismatch. expected " + topic + " got " + dest);
        }

        // 4) UNSUBSCRIBE Alice then SEND again -> Alice should NOT get it
        alice.unsubscribe(aSubId, true);

        String payload2 = "second-" + System.currentTimeMillis();
        bob.send(topic, payload2, true);

        Frame aMsg2 = alice.waitForCommand("MESSAGE", 1000);
        if (aMsg2 != null) {
            die("Alice received MESSAGE after UNSUBSCRIBE (should not). body=" + aMsg2.body);
        }
        System.out.println("[OK] Alice did not receive after UNSUBSCRIBE");

        // 5) DISCONNECT both (with receipt)
        alice.disconnect(true);
        bob.disconnect(true);

        // Close sockets
        alice.close();
        bob.close();

        System.out.println("\n== ALL BASIC TESTS PASSED ==");
        System.out.println("If something fails: paste the harness output + your server console output.");
    }

    // ---------- Client ----------
    static class StompClient {
        private final String name;
        private final String host;
        private final int port;

        private Socket sock;
        private BufferedOutputStream out;
        private Thread readerThread;

        // Frame queue from server
        private final BlockingQueue<Frame> inbox = new LinkedBlockingQueue<>();
        private final AtomicInteger receiptCounter = new AtomicInteger(1);

        StompClient(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        void open() throws IOException {
            sock = new Socket(host, port);
            out = new BufferedOutputStream(sock.getOutputStream());

            readerThread = new Thread(this::readLoop, name + "-reader");
            readerThread.setDaemon(true);
            readerThread.start();

            System.out.println("[" + name + "] connected TCP");
        }

        Frame connect(String login, String passcode, String acceptVersion) throws IOException {
            String frame =
                    "CONNECT\n" +
                    "accept-version:" + acceptVersion + "\n" +
                    "login:" + login + "\n" +
                    "passcode:" + passcode + "\n" +
                    "\n\0";
            sendRaw(frame);
            return waitAnyFrame(1500);
        }

        void subscribe(String destination, String id, boolean wantReceipt) throws IOException {
            String receipt = wantReceipt ? nextReceiptId() : null;
            StringBuilder sb = new StringBuilder();
            sb.append("SUBSCRIBE\n");
            sb.append("destination:").append(destination).append("\n");
            sb.append("id:").append(id).append("\n");
            sb.append("ack:auto\n");
            if (receipt != null) sb.append("receipt:").append(receipt).append("\n");
            sb.append("\n\0");
            sendRaw(sb.toString());

            if (receipt != null) {
                Frame r = waitForReceipt(receipt, 1500);
                if (r == null) {
                    System.out.println("[" + name + "] WARN: no RECEIPT for SUBSCRIBE (maybe your server doesn't implement receipts)");
                } else {
                    System.out.println("[" + name + "] [OK] SUBSCRIBE receipt=" + receipt);
                }
            } else {
                System.out.println("[" + name + "] SUBSCRIBE sent");
            }
        }

        void unsubscribe(String id, boolean wantReceipt) throws IOException {
            String receipt = wantReceipt ? nextReceiptId() : null;
            StringBuilder sb = new StringBuilder();
            sb.append("UNSUBSCRIBE\n");
            sb.append("id:").append(id).append("\n");
            if (receipt != null) sb.append("receipt:").append(receipt).append("\n");
            sb.append("\n\0");
            sendRaw(sb.toString());

            if (receipt != null) {
                Frame r = waitForReceipt(receipt, 1500);
                if (r == null) {
                    System.out.println("[" + name + "] WARN: no RECEIPT for UNSUBSCRIBE");
                } else {
                    System.out.println("[" + name + "] [OK] UNSUBSCRIBE receipt=" + receipt);
                }
            } else {
                System.out.println("[" + name + "] UNSUBSCRIBE sent");
            }
        }

        void send(String destination, String body, boolean wantReceipt) throws IOException {
            String receipt = wantReceipt ? nextReceiptId() : null;
            StringBuilder sb = new StringBuilder();
            sb.append("SEND\n");
            sb.append("destination:").append(destination).append("\n");
            if (receipt != null) sb.append("receipt:").append(receipt).append("\n");
            sb.append("\n");
            sb.append(body);
            sb.append("\0");
            sendRaw(sb.toString());

            if (receipt != null) {
                Frame r = waitForReceipt(receipt, 1500);
                if (r == null) {
                    System.out.println("[" + name + "] WARN: no RECEIPT for SEND");
                } else {
                    System.out.println("[" + name + "] [OK] SEND receipt=" + receipt);
                }
            } else {
                System.out.println("[" + name + "] SEND sent");
            }
        }

        void disconnect(boolean wantReceipt) throws IOException {
            String receipt = wantReceipt ? nextReceiptId() : null;
            StringBuilder sb = new StringBuilder();
            sb.append("DISCONNECT\n");
            if (receipt != null) sb.append("receipt:").append(receipt).append("\n");
            sb.append("\n\0");
            sendRaw(sb.toString());

            if (receipt != null) {
                Frame r = waitForReceipt(receipt, 1500);
                if (r == null) {
                    System.out.println("[" + name + "] WARN: no RECEIPT for DISCONNECT");
                } else {
                    System.out.println("[" + name + "] [OK] DISCONNECT receipt=" + receipt);
                }
            } else {
                System.out.println("[" + name + "] DISCONNECT sent");
            }
        }

        Frame waitForCommand(String command, long timeoutMs) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            List<Frame> stash = new ArrayList<>();
            try {
                while (System.currentTimeMillis() < deadline) {
                    long remain = deadline - System.currentTimeMillis();
                    Frame f = inbox.poll(remain, TimeUnit.MILLISECONDS);
                    if (f == null) break;
                    if (command.equals(f.command)) {
                        // push back others we stashed
                        for (Frame s : stash) inbox.offer(s);
                        return f;
                    }
                    stash.add(f);
                }
            } catch (InterruptedException ignored) {}
            // push back stashed
            for (Frame s : stash) inbox.offer(s);
            return null;
        }

        private Frame waitForReceipt(String receiptId, long timeoutMs) {
            long deadline = System.currentTimeMillis() + timeoutMs;
            List<Frame> stash = new ArrayList<>();
            try {
                while (System.currentTimeMillis() < deadline) {
                    long remain = deadline - System.currentTimeMillis();
                    Frame f = inbox.poll(remain, TimeUnit.MILLISECONDS);
                    if (f == null) break;

                    if ("RECEIPT".equals(f.command) && receiptId.equals(f.headers.get("receipt-id"))) {
                        for (Frame s : stash) inbox.offer(s);
                        return f;
                    }
                    stash.add(f);
                }
            } catch (InterruptedException ignored) {}
            for (Frame s : stash) inbox.offer(s);
            return null;
        }

        private Frame waitAnyFrame(long timeoutMs) {
            try {
                return inbox.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return null;
            }
        }

        private String nextReceiptId() {
            return name.toLowerCase() + "-" + receiptCounter.getAndIncrement();
        }

        private void sendRaw(String s) throws IOException {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            out.write(bytes);
            out.flush();
            // Debug (optional):
            // System.out.println("[" + name + "] >>>\n" + s.replace("\0", "\\0"));
        }

        private void readLoop() {
            try (InputStream in = sock.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                int b;
                while ((b = in.read()) != -1) {
                    if (b == 0) { // '\0' frame terminator
                        Frame frame = Frame.parse(buf.toByteArray());
                        buf.reset();
                        if (frame != null) {
                            inbox.offer(frame);
                            // Debug (optional):
                            // System.out.println("[" + name + "] <<< " + frame.command);
                        }
                    } else {
                        buf.write(b);
                    }
                }
            } catch (IOException e) {
                // Socket closed / server closed
            }
        }

        void close() {
            try { if (sock != null) sock.close(); } catch (IOException ignored) {}
        }
    }

    // ---------- Frame parsing ----------
    static class Frame {
        final String command;
        final Map<String, String> headers;
        final String body;

        Frame(String command, Map<String, String> headers, String body) {
            this.command = command;
            this.headers = headers;
            this.body = body;
        }

        static Frame parse(byte[] rawBytes) {
            String raw = new String(rawBytes, StandardCharsets.UTF_8);

            // Split headers/body by first empty line
            int sep = raw.indexOf("\n\n");
            String headPart;
            String bodyPart;
            if (sep >= 0) {
                headPart = raw.substring(0, sep);
                bodyPart = raw.substring(sep + 2);
            } else {
                headPart = raw;
                bodyPart = "";
            }

            String[] lines = headPart.split("\n");
            if (lines.length == 0) return null;

            String cmd = lines[0].trim();
            Map<String, String> headers = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String k = line.substring(0, colon).trim();
                    String v = line.substring(colon + 1).trim();
                    headers.put(k, v);
                }
            }
            return new Frame(cmd, headers, bodyPart);
        }
    }

    // ---------- Helpers ----------
    static void requireCommand(Frame f, Set<String> allowed, String msg) {
        if (f == null) die(msg + " (got null/no response)");
        if (!allowed.contains(f.command)) die(msg + " (got " + f.command + ")");
        System.out.println("[OK] " + msg + " => " + f.command);
    }

    static void die(String msg) {
        System.out.println("\n[FAIL] " + msg);
        throw new RuntimeException(msg);
    }
}
