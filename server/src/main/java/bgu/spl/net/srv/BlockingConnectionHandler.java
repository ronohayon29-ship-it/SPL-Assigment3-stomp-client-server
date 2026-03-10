package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.StompEncoderDecoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler implements Runnable, ConnectionHandler<String> {

    private final StompMessagingProtocol<String> protocol;
    private final StompEncoderDecoder encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    private volatile boolean connected = true;

    private final Object writeLock = new Object(); // if out null

    private final Connections<String> connections;
    private final int connectionId;

    public BlockingConnectionHandler(Socket sock, StompEncoderDecoder reader, StompMessagingProtocol<String> protocol,
        Connections<String> connections, int connectionId) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connections = connections;
        this.connectionId = connectionId;
    }

    @Override
    public void run() {
        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {
                String nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) {
                    protocol.process(nextMessage);
                }
            }
            

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        finally{
            connected = false;
            connections.disconnect(connectionId);
        }

    }

    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    @Override
    public void send(String msg) {
        //IMPLEMENT IF NEEDED
        if (msg == null) return;
        try {
            synchronized (writeLock) {
                if (out == null) return;
                out.write(encdec.encode(msg));
                out.flush();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }

    }
}
