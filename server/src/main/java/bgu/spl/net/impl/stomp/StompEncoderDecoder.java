package bgu.spl.net.impl.stomp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import bgu.spl.net.api.MessageEncoderDecoder;

public class StompEncoderDecoder implements MessageEncoderDecoder<String> {

    // Accumulates incoming bytes until a null byte indicates the end of a STOMP frame.
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); 

    @Override
    public String decodeNextByte(byte nextByte){
        if (nextByte == 0) { // End of frame
            String ret = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            bytes.reset();
            return ret;
        }

        bytes.write(nextByte);
        return null; 
    }


    @Override
    public byte[] encode(String message){
        String ret = message + "\0";
        return ret.getBytes(StandardCharsets.UTF_8);
    }
}
