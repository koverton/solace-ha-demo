package com.solacesystems.ha.model;

import java.nio.ByteBuffer;

/**
 * Used by the ClusterConnector to serialize/deserialize application messages. Abstracting this from the
 * ClusterConnector and ClusterModel allows different application instances to define their own messages
 * for application input and application state output.
 *
 * @param <InputType> -- input message type
 * @param <OutputType>-- output message type
 */
public interface ClusteredAppSerializer<InputType, OutputType> {

    InputType DeserializeInput(ByteBuffer msg);

    OutputType DeserializeOutput(ByteBuffer msg);

    ByteBuffer SerializeOutput(OutputType output);
}
