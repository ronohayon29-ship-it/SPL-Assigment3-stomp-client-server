package bgu.spl.net.impl.stomp;

import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;


public class ConnectionsImpl<T> implements Connections<T> {

    //<connectionID,ConnectionHandler<T>>
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> connections = new ConcurrentHashMap<>();

    //<channel,<connectionID,SubscribtionID>>
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer,String>> channels = new ConcurrentHashMap<>();

    //<connectionID,<channel,SubscribtionID>>
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String,String>> channelPerID = new ConcurrentHashMap<>();


    private AtomicInteger messageId = new AtomicInteger();

    
    public boolean send(int connectionId, T msg){
        ConnectionHandler<T> handler = connections.get(connectionId);
        if (handler == null) {
            return false;
        }
        handler.send(msg);
        return true;
    }

    public void send(String channel, T msg){
        ConcurrentHashMap<Integer,String> list = channels.get(channel);
        if (list == null) {
            return;
        }
        for(int id: list.keySet()){
            send(id,msg);
        }
    }


    public void disconnect(int connectionId){
        // remove subscriptions of this connection
        Database.getInstance().logout(connectionId);
        ConcurrentHashMap<String, String> myChannels = channelPerID.remove(connectionId);
        if (myChannels != null) {
            for (String channel : myChannels.keySet()) {
                ConcurrentHashMap<Integer, String> subs = channels.get(channel);
                if (subs != null) {
                    subs.remove(connectionId);
                    if (subs.isEmpty()) {
                        channels.remove(channel, subs); // optional cleanup
                    }
                }
            }
        }

        // close handler
        ConnectionHandler<T> handler = connections.remove(connectionId);
        if (handler != null) {
            try {
                handler.close();
            } catch (IOException e) {
                System.out.println("Error " + e);
            }
        }
    }

    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        connections.put(connectionId, handler);
    }



    public boolean subscribe(String channel, int connectionId, String subId){
        ConcurrentHashMap<String, String> my =
        channelPerID.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>());

        if (my.containsKey(channel)) return false;

        if (my.containsValue(subId)) return false;

        channels.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
            .put(connectionId, subId);

        my.put(channel, subId);
        return true;
    }



    public boolean unSubscribe(String subId, int connectionId){
        if (subId == null) return false;

	    // get client's channels map
	    ConcurrentHashMap<String, String> my = channelPerID.get(connectionId);
	    if (my == null) return false;

	    // search which channel the subId is connected to
	    String channelToRemove = null;
	    for (Map.Entry<String, String> entry : my.entrySet()) {
	        if (subId.equals(entry.getValue())) {
	            channelToRemove = entry.getKey();
	            break;
	        }
	    }
	    if (channelToRemove == null) return false;

	    // remove from channel map first
	    ConcurrentHashMap<Integer, String> subs = channels.get(channelToRemove);
	    if (subs != null) {
	        subs.remove(connectionId);
	        if (subs.isEmpty()) {
	            channels.remove(channelToRemove, subs); //clean the empty channel
	        }
	    }

	    // remove from connectionId map
	    my.remove(channelToRemove);
	    if (my.isEmpty()) {
	        channelPerID.remove(connectionId, my);
	    }

	    return true;
    }


    public int getMessageId(){
        return messageId.incrementAndGet();
    }


    public ConcurrentHashMap<Integer,String> getAllSubSChannel(String channel){
        return channels.get(channel);
    }

}
