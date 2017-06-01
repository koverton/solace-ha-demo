package com.solacesystems.ha;

import com.solacesystems.ha.conn.DirectMessageHandler;
import com.solacesystems.ha.conn.SolaceConnector;
import com.solacesystems.solclientj.core.event.FlowEventCallback;
import com.solacesystems.solclientj.core.event.MessageCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.FlowHandle;
import com.solacesystems.solclientj.core.handle.Handle;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class ConnectorFunctionalTests {
    private final static String SUBSCRIPTION_TOPIC = "how/are/you/then/pretty/good/and/you";

    final static String sentMessage = "Hello all you happy people.";
    final static ByteBuffer inbuff = ByteBuffer.allocate(128);
    final static ByteBuffer outbuff = ByteBuffer.allocate(128);
    final static AtomicBoolean gotit = new AtomicBoolean();

    private static SolaceConnector _connector;

    @BeforeClass
    public static void setupConnector() {
        _connector = new SolaceConnector();
        _connector.ConnectSession(ConnectionFields.HOST, ConnectionFields.VPN, ConnectionFields.USER, ConnectionFields.PASS, ConnectorFunctionalTests.class.getName(),
                new SessionEventCallback() {public void onEvent(SessionHandle sessionHandle) {}});
    }

    @AfterClass
    public static void teardownConnector() {
        _connector.DisconnectSession();
    }

    private String getPayloadString(final ByteBuffer buff) {
        int sz = buff.limit();
        byte[] data = new byte[sz];
        buff.get(data, 0, sz);
        return new String(data);
    }

    private void waitabit() {
        for( int i = 0; !gotit.get() && i<5; ++i ) {
            try { Thread.sleep(100); } catch(InterruptedException ex) {}
        }
    }

    @Test
    public void directSubscriberTest() {
        gotit.set(false);

        final DirectMessageHandler handler = new DirectMessageHandler() {
            public String getSubscriptionTopic() {
                return SUBSCRIPTION_TOPIC;
            }

            public void onMessage(String topic, ByteBuffer payload) {
                String msgString = getPayloadString(payload);
                assertEquals(msgString, sentMessage);
                gotit.set(true);
            }

            public ByteBuffer getBuffer() {
                return inbuff;
            }
        };

        _connector.SubscribeDirect( handler );
        outbuff.clear();
        outbuff.put(sentMessage.getBytes());
        _connector.SendBuffer(SUBSCRIPTION_TOPIC, outbuff);

        waitabit();
        assertTrue( gotit.get() );
    }

    @Test
    public void provisionBindSendDeprovisionTest() {
        final String qname = UUID.randomUUID().toString();

        _connector.ProvisionQueue(qname, 1);
        _connector.BindQueue(qname,
                new MessageCallback() {
                    public void onMessage(Handle handle) {
                        gotit.set(true);
                    }
                },
                new FlowEventCallback() {public void onEvent(FlowHandle flowHandle) {}}
        ).start();
        _connector.SendSentinel( qname, "jimmy" );

        waitabit();
        assertTrue( gotit.get() );

        _connector.DeprovisionQueue( qname );

    }
}
