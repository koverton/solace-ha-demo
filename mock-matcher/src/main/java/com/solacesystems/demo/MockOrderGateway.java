package com.solacesystems.demo;
import com.solacesystems.conn.SolaceConnector;
import com.solacesystems.solclientj.core.event.SessionEvent;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

class MockOrderGateway {
    private static final Logger logger = LoggerFactory.getLogger(MockOrderGateway.class);

    public static void main(String[] args)
    {

        if (args.length < 7)
        {
            System.out.println("USAGE: SamplePublisher <HOST> <VPN> <USER> <PASS> <PUB-TOPIC> <STARTID> <SYMBOL>");
            return;
        }
        new MockOrderGateway(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
                .run();
    }

    private MockOrderGateway(String host, String vpn, String username, String password, String topic, String startId, String symbol)
    {
        _startOrderId = Integer.parseInt(startId);
        _outTopic = topic;
        _symbol = symbol;
        _connector = new SolaceConnector();
        _connector.ConnectSession(host, vpn, username, password, "MockOrderGW", new SessionEventCallback() {
            public void onEvent(SessionHandle sessionHandle) {
                handleSessionEvent(sessionHandle.getSessionEvent());
            }
        });
    }

    private void handleSessionEvent(SessionEvent event)
    {
        logger.info("Session event: {}", event);
    }

    private void run()
    {
        boolean running = true;
        int orderId = _startOrderId;
        while (running)
        {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {
                e.printStackTrace();
                running = false;
            }
            sendNextOrder(orderId++);
        }
    }

    private void sendNextOrder(int oid)
    {
        ClientOrder order = OrderHelper.nextOrder(oid, _symbol, 100, 0.25);
        logger.info("Sending msg: {}", order);
        _connector.SendOutput(_outTopic, _serializer.SerializeInput(order));
    }

    private final String _symbol;
    private final int _startOrderId;
    private final String _outTopic;
    private final ByteBuffer _outbuf = ByteBuffer.allocate(8192);
    private final SolaceConnector _connector;
    private final MockMatchingEngineSerializer _serializer = new MockMatchingEngineSerializer();
}
