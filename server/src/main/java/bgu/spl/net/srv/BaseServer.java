package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.stomp.ConnectionsImpl;
import bgu.spl.net.impl.stomp.StompEncoderDecoder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public abstract class BaseServer implements Server<String> {

    private final int port;
    private final Supplier<StompMessagingProtocol<String>> protocolFactory;
    private final Supplier<StompEncoderDecoder> encdecFactory;
    private ServerSocket sock;
    // ours
    private ConnectionsImpl<String> connections = new ConnectionsImpl<>();
    private AtomicInteger currId = new AtomicInteger();

    public BaseServer(
            int port,
            Supplier<StompMessagingProtocol<String>> protocolFactory,
            Supplier<StompEncoderDecoder> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
		this.sock = null;
    }

    @Override
    public void serve() {

        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock; //just to be able to close

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();
                int id = currId.incrementAndGet();

                StompMessagingProtocol<String> protocol = protocolFactory.get();
                protocol.start(id, connections);

                BlockingConnectionHandler handler = new BlockingConnectionHandler(
                        clientSock,
                        encdecFactory.get(),
                        protocol,
                        connections,
                        id);
                connections.addConnection(id,handler);
                execute(handler);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        System.out.println("server closed!!!");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler  handler);

}
