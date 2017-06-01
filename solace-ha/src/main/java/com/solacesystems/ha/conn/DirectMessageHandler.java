package com.solacesystems.ha.conn;


import java.nio.ByteBuffer;

/**
 * Encapsulates subscription to a Direct messaging topic (i.e. not a persistent subscription).
 * Implements a message-handler callback function the client can use to work on inbound messages.
 *
 * This class must provide the topic-subscription string to be subscribed, as well as an
 * allocated ByteBuffer to be used in dispatching inbound messages to the handler function.
 */
public interface DirectMessageHandler {
    /**
     * Returns the topic to be subscribed by the SolaceConnector.
     * @return String topic subscription expression.
     */
    public String getSubscriptionTopic();

    /**
     * Implements a message handler for inbound binary messages.
     *
     * @param topic the full topic String on which the message was published
     * @param payload the binary payload of the message wrapped in this handler's ByteBuffer instance
     */
    public void onMessage(String topic, ByteBuffer payload);

    /**
     * Provides the ByteBuffer container in which to copy inbound messages on this subscription.
     *
     * @return A fully-allocated ByteBuffer the SolaceConnector can copy messages into upon arrival and dispatching.
     */
    public ByteBuffer getBuffer();
}
