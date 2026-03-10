package bgu.spl.net.impl.stomp;

import java.util.LinkedHashMap;
import java.util.Map;


 // Parser/builder for STOMP frames 
 
public class StompParser {


    // Parse raw frame string into StompFrame.
    public StompFrame parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("raw is null");

        // strip null terminator
        String s = raw;
        s = stripNullTerminator(s);

        // seperate header and body frame
        String headerPart;
        String bodyPart;
        int sep = s.indexOf("\n\n");
        if (sep >= 0) {
            headerPart = s.substring(0, sep);
            bodyPart = s.substring(sep + 2);
        } else {
            headerPart = s;
            bodyPart = "";
        }

        // header lines
        String[] lines = headerPart.split("\n", -1);
        if (lines.length == 0 || lines[0].trim().isEmpty()) {
            throw new IllegalArgumentException("Missing command line");
        }

        String command = lines[0].trim();
        Map<String, String> headers = new LinkedHashMap<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Bad header line: '" + line + "'");
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1);
            headers.put(key, value);
        }

        return new StompFrame(command, headers, bodyPart);
    }





     // Build a raw STOMP frame string from StompFrame.
    public String build(StompFrame frame) {
        if (frame == null) throw new IllegalArgumentException("frame is null");

        StringBuilder sb = new StringBuilder();
        sb.append(frame.getCommand()).append('\n');

        for (Map.Entry<String, String> e : frame.getHeaders().entrySet()) {
            sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        }

        sb.append('\n'); // blank line between headers and body
        sb.append(frame.getBody() == null ? "" : frame.getBody());
        sb.append('\0');
        return sb.toString();
    }





    
    private static String stripNullTerminator(String s) {
        // Remove the first '\0' and anything after it (STOMP terminator)
        int idx = s.indexOf('\0');
        if (idx >= 0) return s.substring(0, idx);
        return s;
    }
}
