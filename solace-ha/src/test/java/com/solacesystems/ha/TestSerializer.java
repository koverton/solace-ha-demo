package com.solacesystems.ha;

import com.solacesystems.ha.model.ClusteredAppSerializer;

import java.nio.ByteBuffer;

/**
 * Implements a basic serializer/deserializer for our application's Input and Output Types.
 * This will be by the HA library to convert to/from the underlying transport message formats.
 */
class TestSerializer implements ClusteredAppSerializer<Integer,Double> {
    private final ByteBuffer _outbuff = ByteBuffer.allocate(10);
    private final ByteBuffer _inbuff = ByteBuffer.allocate(10);

    public ByteBuffer SerializeOutput(Double output) {
        _outbuff.clear();
        _outbuff.putDouble(output);
        return _outbuff;
    }

    public Double DeserializeOutput(ByteBuffer msg) {
        return msg.getDouble();
    }

    public Integer DeserializeInput(ByteBuffer msg) {
        return msg.getInt();
    }

    /** Not actually needed, but useful for our test to push inputs **/
    public ByteBuffer SerializeInput(Integer input) {
        _inbuff.clear();
        _inbuff.putInt(input);
        return _inbuff;
    }
}
