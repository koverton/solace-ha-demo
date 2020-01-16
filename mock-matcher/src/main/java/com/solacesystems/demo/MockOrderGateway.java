package com.solacesystems.demo;
import com.solacesystems.ha.conn.SolaceConnector;
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

        if (args.length < 8)
        {
            System.out.println("USAGE: MockOrderGateway <HOST> <VPN> <USER> <PASS> <PUB-TOPIC> <STARTID> <SYMBOL> <MID>");
            return;
        }
        new MockOrderGateway(args[0], args[1], args[2], args[3], args[4], args[5], args[6], Double.parseDouble(args[7]))
                .run();
    }

    private MockOrderGateway(String host, String vpn, String username, String password, String topic, String startId, String symbol, double midPrice)
    {
        _startOrderId = Integer.parseInt(startId);
        _outTopic = topic;
        _symbol = symbol;
        _mid = midPrice;
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
        ClientOrder order = OrderHelper.nextOrder(oid, _symbol, _mid, 0.25);
        logger.info("Sending msg: {}", order);
        _connector.SendBuffer(_outTopic, _serializer.SerializeInput(order));
    }

    private final String _symbol;
    private final double _mid;
    private final int _startOrderId;
    private final String _outTopic;
    private final ByteBuffer _outbuf = ByteBuffer.allocate(8192);
    private final SolaceConnector _connector;
    private final MockMatchingEngineSerializer _serializer = new MockMatchingEngineSerializer();
}
