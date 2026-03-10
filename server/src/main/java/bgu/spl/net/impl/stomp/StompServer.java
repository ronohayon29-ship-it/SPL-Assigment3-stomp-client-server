package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.Server;

public class StompServer {

    public static void main(String[] args) {
        // TODO: implement this

        if (args.length < 2) {
            printUsageAndExit();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port: " + args[0]);
            printUsageAndExit();
            return;
        }

        String mode = args[1].toLowerCase();


        switch (mode) {
            case "tpc":
                Server.threadPerClient(port, () -> new StompMessagingProtocolImpl(), () -> new StompEncoderDecoder()).serve();
                break;

            case "reactor":
                // number of selector threads – typical default is Runtime.getRuntime().availableProcessors()
                int nThreads = Runtime.getRuntime().availableProcessors();
                Server.reactor(nThreads, port, () -> new StompMessagingProtocolImpl(), () -> new StompEncoderDecoder()).serve();
                break;

            default:
                System.out.println("Unknown mode: " + args[1]);
                printUsageAndExit();
        }
    }

    private static void printUsageAndExit() {
        System.out.println("Usage: java StompServer <port> <tpc|reactor>");
        System.out.println("Example: java StompServer 7777 tpc");
        System.out.println("Example: java StompServer 7777 reactor");
    }
}

