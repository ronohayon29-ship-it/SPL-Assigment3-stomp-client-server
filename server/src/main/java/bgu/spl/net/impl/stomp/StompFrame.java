package bgu.spl.net.impl.stomp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Data-only representation of a STOMP frame.
 * command: CONNECT, SEND, SUBSCRIBE, DISCONNECT...
 * headers: key:value lines
 * body: everything after the blank line
 */
public final class StompFrame {
    private final String command;
    private final Map<String, String> headers;
    private final String body;

    public StompFrame(String command, Map<String, String> headers, String body) {
        this.command = validCommand(command);
        Map<String, String> copy = new LinkedHashMap<>();
        if (headers != null) copy.putAll(headers);
        this.headers = Collections.unmodifiableMap(copy);
        this.body = (body == null) ? "" : body;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String key) {
        return headers.get(key);
    }

    public String getBody() {
        return body;
    }

    public boolean hasHeader(String key) {
        return headers.containsKey(key);
    }

    public StompFrame withHeader(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(this.headers);
        copy.put(key, value);
        return new StompFrame(this.command, copy, this.body);
    }

    public StompFrame withBody(String newBody) {
        return new StompFrame(this.command, this.headers, newBody);
    }

    private static String validCommand(String cmd) {
        if (cmd == null) throw new IllegalArgumentException("command is null");
        String c = cmd.trim();
        if (c.isEmpty()) throw new IllegalArgumentException("command is empty");
        return c;
    }

    @Override
    public String toString() {
        return "StompFrame{" +
                "command='" + command + '\'' +
                ", headers=" + headers +
                ", bodyLen=" + body.length() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StompFrame)) return false;
        StompFrame other = (StompFrame) o;
        return Objects.equals(command, other.command)
                && Objects.equals(headers, other.headers)
                && Objects.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, headers, body);
    }
}
