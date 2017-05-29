package com.solacesystems.conn;

import com.solacesystems.solclientj.core.*;
import com.solacesystems.solclientj.core.event.*;
import com.solacesystems.solclientj.core.handle.*;
import com.solacesystems.solclientj.core.resource.*;

import java.nio.ByteBuffer;
import java.util.logging.Level;

import com.solacesystems.solclientj.core.SolEnum.ProvisionFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.solacesystems.solclientj.core.SolEnum.*;
import static com.solacesystems.solclientj.core.handle.SessionHandle.*;

public class SolaceConnector {
    private static final Logger logger = LoggerFactory.getLogger(SolaceConnector.class);

    public SolaceConnector() throws IllegalStateException {
        int rc = Solclient.init(new String[0]);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to init Solace Client Library");
        Solclient.setLogLevel(Level.INFO);
        rc = Solclient.createContextForHandle(_ctx, new String[0]);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to allocate Solace context handle");
        rc = Solclient.createMessageForHandle(_outmsg);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to allocate Solace message handle");
        _outmsg.setMessageDeliveryMode(MessageDeliveryMode.PERSISTENT);
    }

    public void destroy() {
        Helper.destroyHandle(_outmsg);
        Helper.destroyHandle(_sess);
        Helper.destroyHandle(_ctx);
    }

    public void ConnectSession(String host, String vpn, String user, String pass, String clientName, SessionEventCallback eventHandler) throws SolclientException {

        final String[] props = new String[22];
        int i = 0;
        props[i++] = PROPERTIES.HOST;               props[i++] = host;
        props[i++] = PROPERTIES.VPN_NAME;           props[i++] = vpn;
        props[i++] = PROPERTIES.USERNAME;           props[i++] = user;
        props[i++] = PROPERTIES.PASSWORD;           props[i++] = pass;
        props[i++] = PROPERTIES.CLIENT_NAME;        props[i++] = clientName;
        props[i++] = PROPERTIES.CONNECT_RETRIES;    props[i++] = "5";
        props[i++] = PROPERTIES.CONNECT_TIMEOUT_MS; props[i++] = "1000";
        props[i++] = PROPERTIES.RECONNECT_RETRIES;  props[i++] = "300";
        props[i++] = PROPERTIES.KEEP_ALIVE_LIMIT;   props[i++] = "3";
        props[i++] = PROPERTIES.KEEP_ALIVE_INT_MS;  props[i++] = "1000";
        props[i++] = PROPERTIES.TOPIC_DISPATCH;     props[i] = BooleanValue.ENABLE;

        int rc = _ctx.createSessionForHandle(_sess, props, new MessageCallback() {
            public void onMessage(Handle handle) {
                logger.error("MAYDAY! SHOULD NOT BE ANY DIRECT MESSAGES!");
            }
        }, eventHandler);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to create Solace session handle");

        _sess.connect();

        if (!_sess.isCapable(CapabilityName.CAPABILITY_TRANSACTED_SESSION)) {
            throw new IllegalStateException(
                    "Required Capability is not present: Transacted session not supported.");
        }
    }

    public void DisconnectSession() {
        _sess.disconnect();
    }

    public boolean ProvisionQueue(String name, int quotaMB) {
        int queueProps = 0;

        String[] queueProperties = new String[10];

        queueProperties[queueProps++] = Endpoint.PROPERTIES.PERMISSION;
        queueProperties[queueProps++] = EndpointPermission.DELETE;

        queueProperties[queueProps++] = Endpoint.PROPERTIES.QUOTA_MB;
        queueProperties[queueProps++] = Integer.toString(quotaMB);

        // The Queue with name
        Queue queue = Solclient.Allocator.newQueue(name, queueProperties);

        int rc = _sess.provision(queue,
                ProvisionFlags.WAIT_FOR_CONFIRM|ProvisionFlags.IGNORE_EXIST_ERRORS,
                0);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to provision Solace queue.");
        return (rc == ReturnCode.OK);
    }

    public boolean DeprovisionQueue(String name) {
        // The Queue with name
        Queue queue = Solclient.Allocator.newQueue(name, new String[0]);
        int rc = _sess.deprovision(queue,
                ProvisionFlags.WAIT_FOR_CONFIRM|ProvisionFlags.IGNORE_EXIST_ERRORS,
                0);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to deprovision Solace queue.");
        return (rc == ReturnCode.OK);
    }

    public FlowHandle BindQueue(String name, MessageCallback msgHandler, FlowEventCallback flowEventHandler) {
        int i = 0;
        String[] props = new String[8];

        props[i++] = FlowHandle.PROPERTIES.BIND_BLOCKING;  props[i++] = BooleanValue.ENABLE;
        props[i++] = FlowHandle.PROPERTIES.ACKMODE;        props[i++] = AckMode.AUTO;
        props[i++] = FlowHandle.PROPERTIES.ACTIVE_FLOW_IND;props[i++] = BooleanValue.ENABLE;
        props[i++] = FlowHandle.PROPERTIES.START_STATE;    props[i]   = BooleanValue.DISABLE;

        Queue queue = Solclient.Allocator.newQueue(name, null);

        FlowHandle flowHandle = Solclient.Allocator.newFlowHandle();
        int rc = _sess.createFlowForHandle(flowHandle, props, queue, null, msgHandler, flowEventHandler);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to create Solace queue binding flow handle");

        return flowHandle;
    }

    public void SubscribeDirect(final DirectMessageHandler handler) {
        _sess.subscribe(
                Solclient.Allocator.newMessageDispatchTargetHandle(
                    Solclient.Allocator.newTopic( handler.getSubscriptionTopic() ),
                    new MessageCallback() {
                        public void onMessage(Handle handle) {
                            MessageSupport ms = (MessageSupport) handle;
                            MessageHandle msg = ms.getRxMessage();
                            String topic = msg.getDestination().getName();
                            ByteBuffer container = handler.getBuffer();
                            container.clear();
                            msg.getBinaryAttachment( container );
                            container.flip();
                            handler.onMessage( topic, container );
                        }
                    },
                    false
                ),
                SubscribeFlags.WAIT_FOR_CONFIRM,
                0
        );
    }

    public void SubscribeQueueToTopic(String name, String subscription) {
        Queue queue = Solclient.Allocator.newQueue(name, null);
        Topic topic = Solclient.Allocator.newTopic(subscription);

        int rc = _sess.subscribe(queue, topic, SubscribeFlags.WAIT_FOR_CONFIRM, 0);
        if (rc != ReturnCode.OK)
            throw new IllegalStateException("Failed to bind Solace queue to topic");
    }

    public void SendBuffer(String sendTopic, ByteBuffer payload) {
        payload.flip();
        _outmsg.setBinaryAttachment(payload);
        _outmsg.deleteApplicationMessageType();
        _outmsg.setDestination(Solclient.Allocator.newTopic(sendTopic));
        int rc = _sess.send(_outmsg);
        if (rc != ReturnCode.OK)
            logLastError( "When sending output, session.send() returned " + ReturnCode.toString(rc));
    }

    public void SendSentinel(String queueName, String msgType) {
        _outmsg.setDestination(Solclient.Allocator.newQueue(queueName));
        _outmsg.setApplicationMessageType(msgType);
        int rc = _sess.send(_outmsg);
        if (rc != ReturnCode.OK)
            logLastError("When sending Sentinel, session.send() returned " + ReturnCode.toString(rc));
    }

    private void logLastError(String message) {
        logger.error( "ERROR: " + message );
        SolclientErrorInfo info = Solclient.getLastErrorInfo();
        if (info != null) {
            logger.error(" Error [" + info.getErrorStr()
                    + "] SubCode [" + info.getSubCode()
                    + "]");
        }
    }

    private final ContextHandle _ctx = Solclient.Allocator.newContextHandle();
    private final SessionHandle _sess = Solclient.Allocator.newSessionHandle();
    private final MessageHandle _outmsg = Solclient.Allocator.newMessageHandle();
}
