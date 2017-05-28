package com.solacesystems.demo;

import com.solacesystems.model.ClusteredAppSerializer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

class MockMatchingEngineSerializer implements ClusteredAppSerializer<ClientOrder, MatcherState> {
    private static final Logger logger = LoggerFactory.getLogger(MockMatchingEngineSerializer.class);

    public ByteBuffer SerializeInput(ClientOrder order) {
        _outmsgbuf.clear();
        String jsonstr = JSONSerializer.SerializeClientOrder(order).toJSONString();
        _outmsgbuf.put(jsonstr.getBytes());
        return _outmsgbuf;
    }

    public ClientOrder DeserializeInput(ByteBuffer msg) {
        ClientOrder order = null;
        try {
            String jsonStr = getJsonString( msg );
            JSONObject json = (JSONObject) _parser.parse( jsonStr );
            order = JSONSerializer.DeserializeClientOrder( json );
        }
        catch (ParseException pe) {
            logger.error("Error parsing incoming message");
            pe.printStackTrace();
        }
        return order;
    }

    public ByteBuffer SerializeOutput(MatcherState output) {
        _outmsgbuf.clear();
        String jsonstr = JSONSerializer.SerializeMatcherState(output).toJSONString();
        _outmsgbuf.put(jsonstr.getBytes());
        return _outmsgbuf;
    }

    public MatcherState DeserializeOutput(ByteBuffer msg) {
        MatcherState state = null;
        try {
            String jsonStr = getJsonString( msg );
            JSONObject json = (JSONObject) _parser.parse( jsonStr );
            state = JSONSerializer.DeserializeMatcherState( json );
        }
        catch(ParseException pe) {
            logger.error("Error parsing incoming JSON message");
            pe.printStackTrace();
        }
        return state;
    }

    private String getJsonString(ByteBuffer buff) {
        int sz = buff.limit();
        logger.debug("Getting content from bytebuffer with limit: {}", sz);
        byte[] data = new byte[sz];
        buff.get(data, 0, sz);
        String out = new String(data);
        logger.debug("Getting JSON content: {}", out);
        return out;
    }

    private final JSONParser _parser    = new JSONParser();
    private final ByteBuffer _outmsgbuf = ByteBuffer.allocate(8192);
}