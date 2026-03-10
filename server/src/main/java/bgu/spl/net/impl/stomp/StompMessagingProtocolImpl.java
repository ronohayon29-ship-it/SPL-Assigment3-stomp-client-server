package bgu.spl.net.impl.stomp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    
    private int connectionId = 0;
    private Connections<String> connections = null;

    private boolean shouldTerminate = false;

    private StompParser parser = new StompParser();

    @Override
    public void start(int connectionId, Connections<String> connections){
        this.connectionId = connectionId;
        this.connections = connections;
    }
    
    @Override
    public void process(String message){
        StompFrame frame = parser.parse(message);
        String command = frame.getCommand();

        if (!"CONNECT".equals(command)) {
            if (!Database.getInstance().isClientConnected(connectionId)) {
                connections.send(connectionId, buildError("User is not connected"));
                shouldTerminate = true;
                return;
            }
        }
        switch (command) {
            case "CONNECT":
                String msg = connect(frame);
                connections.send(connectionId,msg);
                break;
            case "DISCONNECT":
                String msg_dis = disconnected(frame);
                connections.send(connectionId, msg_dis);
                shouldTerminate = true;
            //    connections.disconnect(connectionId);            need to put in handler
                break;
            case "SUBSCRIBE":
                String msg_sub = subscribe(frame);
                if(msg_sub!=null){
                    connections.send(connectionId, msg_sub);
                }
                break;
            case "UNSUBSCRIBE":
                String msg_Un = unsubscribe(frame);
                if(msg_Un!=null){
                    connections.send(connectionId, msg_Un);
                }
                break;
            case "SEND":
                String msg_send = send(frame);
                if(msg_send!=null){
                    connections.send(connectionId, msg_send);
                }
                break;
        
            default:
                break;
        }

    }
	
	@Override
    public boolean shouldTerminate(){
        return shouldTerminate;
    }

    private String connect(StompFrame frame){
       
        String login = frame.getHeader( "login");
        String passcode = frame.getHeader( "passcode");

        if (isBlank(login) || isBlank(passcode)) {
            shouldTerminate = true;
            return buildError("Missing required headers: login/passcode");
        }

        Database db = Database.getInstance();
        LoginStatus st = db.login(connectionId, login, passcode);

        switch (st) {

            case ADDED_NEW_USER:
            case LOGGED_IN_SUCCESSFULLY: {

                Map<String, String> h = new LinkedHashMap<>();
                h.put("version", "1.2");
                return parser.build(new StompFrame("CONNECTED", h, ""));
            }

            case WRONG_PASSWORD: {
                shouldTerminate = true;
                return buildError("Wrong password");
            }

            case ALREADY_LOGGED_IN: {
                shouldTerminate = true;
                return buildError("User already logged in");
            }

            case CLIENT_ALREADY_CONNECTED: {
                shouldTerminate = true;
                return buildError("Client already connected");
            }

            default: {
                shouldTerminate = true;
                return buildError("Connection failed");
            }
        }
    }



     private String disconnected(StompFrame frame){
        String receipt = frame.getHeader("receipt");
        Database db = Database.getInstance();
        if (receipt == null || receipt.isEmpty()) {
            Map<String,String> errorMap = new LinkedHashMap<>();
            errorMap.put("message", "Missing receipt header");
            shouldTerminate = true;
            return parser.build(new StompFrame("ERROR", errorMap, ""));
        }
        Map<String,String> receiptMap = new LinkedHashMap<>();
        receiptMap.put("receipt-id", receipt);
        db.logout(connectionId);
        return parser.build(new StompFrame("RECEIPT", receiptMap,""));
    }


    private String subscribe(StompFrame frame){
        String des = frame.getHeader("destination");
        String subId = frame.getHeader("id");
        String receipt = frame.getHeader("receipt");
        if(des == null || subId == null || des.isEmpty() || subId.isEmpty() ){
            Map<String,String> errorMap = new LinkedHashMap<>();
            if(receipt!=null && !receipt.isEmpty()){
                errorMap.put("receipt-id", receipt);
            }
            errorMap.put("message", "destination or subId not valid");
            shouldTerminate = true;
            return parser.build(new StompFrame("ERROR", errorMap,""));
        }
        

        if(((ConnectionsImpl<String>) connections).subscribe(des, connectionId, subId)){
            if(receipt != null && !receipt.isEmpty()){
                Map<String,String> recMap = new LinkedHashMap<>();
                recMap.put("receipt-id", receipt);
                return parser.build(new StompFrame("RECEIPT", recMap, ""));
            }
            return null;
        }
        
        Map<String,String> errorMap = new LinkedHashMap<>();
        if(receipt!=null&&!receipt.isEmpty()){
            errorMap.put("receipt-id", receipt);
        }
        errorMap.put("message", "Already connected to the channel or subId number already assigned to another channel");
        shouldTerminate = true;
        return parser.build(new StompFrame("ERROR", errorMap,""));
    }



    private String unsubscribe(StompFrame frame){
        String subId = frame.getHeader("id");
        String receipt = frame.getHeader("receipt");
        if(subId == null || subId.isEmpty() ){
            Map<String,String> errorMap = new LinkedHashMap<>();
            if(receipt!=null && !receipt.isEmpty()){
                errorMap.put("receipt-id", receipt);
            }
            errorMap.put("message", "subId isn't valid");
            shouldTerminate = true;
            return parser.build(new StompFrame("ERROR", errorMap,""));
        }

        if(((ConnectionsImpl<String>) connections).unSubscribe(subId, connectionId)){
            if(receipt != null && !receipt.isEmpty()){
                Map<String,String> recMap = new LinkedHashMap<>();
                recMap.put("receipt-id", receipt);
                return parser.build(new StompFrame("RECEIPT", recMap, ""));
            }
            return null;
        }

        Map<String,String> errorMap = new LinkedHashMap<>();
        if(receipt!=null&&!receipt.isEmpty()){
            errorMap.put("receipt-id", receipt);
        }
        errorMap.put("message", "subId doesn't exist for the client");
        shouldTerminate = true;
        return parser.build(new StompFrame("ERROR", errorMap,""));
    }



    private String send(StompFrame frame){
        String receipt = frame.getHeader("receipt");
        String des = frame.getHeader("destination");
        String filename = frame.getHeader("filename");

        if(des == null || des.isEmpty()){
                shouldTerminate = true;
                Map<String,String> errorMap = new LinkedHashMap<>();
                if(receipt != null && !receipt.isEmpty()){
                        errorMap.put("receipt-id", receipt);
                }
                errorMap.put("message", "destination isn't valid");
                return parser.build(new StompFrame("ERROR", errorMap,""));
        }

        String body = frame.getBody();

        //ours - in order to get server report
        if ("/admin/report".equals(des)) {
            Database.getInstance().printReport();
            return null;
        }

        ConcurrentHashMap<Integer,String> mapDes =
                ((ConnectionsImpl<String>) connections).getAllSubSChannel(des);
        
        if(!mapDes.containsKey(connectionId)){
            shouldTerminate = true;
                Map<String,String> errorMap = new LinkedHashMap<>();
                if(receipt != null && !receipt.isEmpty()){
                        errorMap.put("receipt-id", receipt);
                }
                errorMap.put("message", "client not subscribing this channel");
                return parser.build(new StompFrame("ERROR", errorMap,""));
        }

        if (mapDes != null) {
                for (Map.Entry<Integer, String> entry : mapDes.entrySet()){
                        int targetId = entry.getKey();
                        String subId = entry.getValue();

                        int messageId = ((ConnectionsImpl<String>) connections).getMessageId();

                        Map<String,String> headersMap = new LinkedHashMap<>();
                        headersMap.put("subscription", subId);
                        headersMap.put("message-id", "" + messageId);
                        headersMap.put("destination", des);

                        String msg = parser.build(new StompFrame("MESSAGE", headersMap, body));
                        ((ConnectionsImpl<String>) connections).send(targetId, msg);
                }
        }

        if(filename!=null){
            String user = Database.getInstance().getUserName(connectionId);
            Database.getInstance().trackFileUpload(user, filename, des);
        }

        if(receipt != null && !receipt.isEmpty()){
                Map<String,String> recMap = new LinkedHashMap<>();
                recMap.put("receipt-id", receipt);
                return parser.build(new StompFrame("RECEIPT", recMap, ""));
        }

        
        return null;
    }


    private String buildError(String msg) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("message", msg);
        return parser.build(new StompFrame("ERROR", h, ""));
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
