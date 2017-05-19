package com.solacesystems.model;

import com.solacesystems.conn.Helper;
import com.solacesystems.conn.SolaceConnector;
import com.solacesystems.solclientj.core.SolEnum;
import com.solacesystems.solclientj.core.SolclientException;
import com.solacesystems.solclientj.core.event.*;
import com.solacesystems.solclientj.core.handle.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Primary clustering logic performed here. This class connects to a Solace Exclusive Queue
 * and related State-Queue to participate in a cluster of Hot/Warm event handling application
 * instances. This class tracks the following state:
 * - HA State: whether the instance is Active or Backup; Active instances are responsible for
 *             processing input and tracking application state per input and outputting
 *             that state to downstream consumers and peer state-queues. The Backup instances
 *             are responsible for processing State output via their state-queue.
 *
 * - Sequence State: where the application instance is with respect to processing the output
 *                   state from the LIVE member. For example, when an instance joins the cluster
 *                   it will simply process state-queue messages. When it receive ACTIVE flow
 *                   indication on the input-queue it empties the state-queue, changes it's state
 *                   to UP_TO_DATE before starting the input queue flow. As it processes input
 *                   the ACTIVE member publishes state messages to it's standard output topic.
 *
 * - Last known output state from the application
 *
 * - Last known input state to the application
 *
 * @param <InputType> -- input message type
 * @param <OutputType>-- output message type
 */
public class ClusterConnector<InputType, OutputType> {
    private static final Logger logger = LoggerFactory.getLogger(ClusterConnector.class);

    ////////////////////////////////////////////////////////////////////////
    //////////            Public Interface                         /////////
    ////////////////////////////////////////////////////////////////////////

    public ClusterConnector(ClusterModel<InputType, OutputType> model,
                            ClusteredAppSerializer<InputType, OutputType> serializer) {
        _model = model;
        _serializer = serializer;
        _connector = new SolaceConnector();
        initState();
    }

    public void Connect(String host, String vpn, String user, String pass, String clientName) throws SolclientException {
        _connector.ConnectSession(host, vpn, user, pass, clientName,
                new SessionEventCallback() {
                    public void onEvent(SessionHandle sessionHandle) {
                        onSessionEvent(sessionHandle.getSessionEvent());
                    }
                });
    }

    public void BindQueues(String inputQueue, String inputSubscription, String stateQueue, String outputSubscription) {
        // Wait until the Solace Session is UP before binding to queues
        boolean connected = false;
        _inputQueueName = inputQueue;
        _stateQueueName = stateQueue;
        while(!connected) {
            if (_model.GetHAStatus() == HAState.CONNECTED) {
                _connector.ProvisionQueue(inputQueue, 150);
                _connector.SubscribeQueueToTopic(inputQueue, inputSubscription);
                _connector.ProvisionQueue(stateQueue, 150);
                _connector.SubscribeQueueToTopic(stateQueue, outputSubscription);
                // The order of instantiation matters; inputflow is used for active-flow-ind
                // which triggers recovering state via browser, then starts appflow
                // after recovery completes
                _model.SetSequenceStatus(SeqState.BOUND);
                _stateflow = _connector.BindQueue(stateQueue,
                        new MessageCallback() {
                            public void onMessage(Handle handle) {
                                MessageSupport ms = (MessageSupport) handle;
                                onStateMessage(ms.getRxMessage());
                            }
                        },
                        new FlowEventCallback() {
                            public void onEvent(FlowHandle flowHandle) {
                                FlowEvent event = flowHandle.getFlowEvent();
                                onStateFlowEvent(event);
                            }
                        });
                _inputflow = _connector.BindQueue(inputQueue,
                        new MessageCallback() {
                            public void onMessage(Handle handle) {
                                MessageSupport ms = (MessageSupport) handle;
                                onInputMessage(ms.getRxMessage());
                            }
                        },
                        new FlowEventCallback() {
                            public void onEvent(FlowHandle flowHandle) {
                                FlowEvent event = flowHandle.getFlowEvent();
                                onInputFlowEvent(event);
                            }
                        });
                _stateflow.start();
                connected = true;
            }
        }
    }

    public void SendSerializedOutput(String topic, ByteBuffer output) {
        // Just in case you need to send multiple outputs
        _connector.SendOutput(topic, output);
    }

    public void SendOutput(String activeTopic, String standbyTopic, OutputType output) {
        // If we're the active member of the cluster, we are responsible
        // for all output but don't publish until we have new input data
        if (_model.GetHAStatus() == HAState.ACTIVE)
        {
            if(_model.GetSequenceStatus() == SeqState.UP_TO_DATE)
                _connector.SendOutput(activeTopic, _serializer.SerializeOutput(output));
        }
        else {
            _connector.SendOutput(standbyTopic, _serializer.SerializeOutput(output));
        }

    }

    public void Destroy() {
        if (_inputflow != null) {
            _inputflow.stop();
            Helper.destroyHandle(_inputflow);
        }
        if (_stateflow != null) {
            _stateflow.stop();
            Helper.destroyHandle(_stateflow);
        }
        _connector.DisconnectSession();
        _connector.destroy();
    }

    ////////////////////////////////////////////////////////////////////////
    //////////            Event Handlers                           /////////
    ////////////////////////////////////////////////////////////////////////

    /**
     * Invoked on the Solace session; this is used to indicate when the
     * connection is UP/Down or reconnecting
     *
     * @param event -- the Solace session connectivity event
     */
    private void onSessionEvent(SessionEvent event) {
        switch(event.getSessionEventCode()) {
            case SolEnum.SessionEventCode.UP_NOTICE:
                _model.SetHAStatus(HAState.CONNECTED);
                _model.SetSequenceStatus(SeqState.CONNECTED);
                break;
            case SolEnum.SessionEventCode.DOWN_ERROR:
                _model.SetHAStatus(HAState.DISCONNECTED);
                _model.SetSequenceStatus(SeqState.INIT);
                break;
            case SolEnum.SessionEventCode.RECONNECTING_NOTICE:
                break;
            case SolEnum.SessionEventCode.RECONNECTED_NOTICE:
                break;
            default:
                break;
        }
    }

    /**
     * Invoked on the application queue flow object when a flow event occurs;
     * used to elect the ACTIVE member of the cluster
     *
     * @param event -- the flow event for the application queue
     */
    private void onInputFlowEvent(FlowEvent event) {
        logger.debug("Input queue flow event: " + event);
        switch (event.getFlowEventEnum())
        {
            case SolEnum.FlowEventCode.UP_NOTICE:
                break;
            case SolEnum.FlowEventCode.ACTIVE:
                becomeActive();
                break;
            case SolEnum.FlowEventCode.INACTIVE:
                becomeBackup();
                break;
            default:
                break;
        }
    }

    /**
     * Invoked on the inputflow when an input message arrives
     *
     * @param msg -- new solace message from the input queue
     */
    private void onInputMessage(MessageHandle msg) {
        _inbuff.clear();
        msg.getBinaryAttachment(_inbuff);
        _inbuff.flip();
        processInputMsg(_serializer.DeserializeInput(_inbuff));
    }

    private void onStateFlowEvent(FlowEvent event) {
        logger.debug("State queue flow event: " + event);
        switch (event.getFlowEventEnum()) {
            case SolEnum.FlowEventCode.UP_NOTICE:
                break;
            case SolEnum.FlowEventCode.ACTIVE:
                _model.SetHAStatus(HAState.BACKUP);
                _model.SetSequenceStatus(SeqState.RECOVERING);
                break;
            case SolEnum.FlowEventCode.INACTIVE:
                break;
            default:
                break;
        }
    }

    /**
     * Invoked on the State Queue flowhandle; these events should contain
     * the output messages from the ACTIVE member
     *
     * @param msg -- solace msg read from the State Queue
     */
    private void onStateMessage(MessageHandle msg) {
        String msgtype = msg.getApplicationMessageType();
        if (msgtype != null && msgtype.equals(SENTINEL))
            processStateMessage(null, true);
        else {
            _outbuff.clear();
            msg.getBinaryAttachment(_outbuff);
            _outbuff.flip();
            processStateMessage(_serializer.DeserializeOutput(_outbuff), false);
        }
    }

    ////////////////////////////////////////////////////////////////////////
    //////////          State Transitions                          /////////
    ////////////////////////////////////////////////////////////////////////
    private void initState() {
        _model.SetLastOutput(null);
        _model.SetHAStatus(HAState.DISCONNECTED);
        _model.SetSequenceStatus(SeqState.INIT);
    }

    /**
     * Invoked on the StateFlow flowhandle when message arrives
     *
     * @param state -- a message from the state-queue read to keep up with the ACTIVE processor state
     */
    private void processStateMessage(OutputType state, boolean isSentinel) {
        if (isSentinel) {
            logger.info("Finished recovering state!");
            _model.SetSequenceStatus(SeqState.UP_TO_DATE);
            _model.SetHAStatus(HAState.ACTIVE);
            _inputflow.start(); // if a msg arrives it is passed to processLastOutputMsg (below)
        }
        else {
            _model.SetHAStatus(HAState.BACKUP);
            _model.SetLastOutput(state);
        }
    }

    /**
     * Invoked on the inputflow when an application message arrives. If
     * the current position in the application sequence is up to date
     * with the last output, then this function calls the application
     * listener to give it a chance to update its current application
     * state and output something representing that state.
     *
     * @param input -- new application input message
     */
    private void processInputMsg(InputType input) {
        // Construct a new app state
        _model.UpdateApplicationState(input);
    }

    /**
     * Invoked on the input when flow UP event occurs or when flow changes
     * from INACTIVE to ACTIVE This function tries to catch up on all messages
     * from the state queue to recover the last output state from this application
     * before processing any input messages.
     */
    private void recoverAllState() {
        logger.info(
                "Recovering all state from the state queue, current sequence state is {} and sending sentinel message.",
                _model.GetSequenceStatus());
        _model.SetSequenceStatus(SeqState.RECOVERING);
        _connector.SendSentinel(_stateQueueName, SENTINEL);
    }

    /**
     * Invoked on the inputflow when flow ACTIVE event occurs
     */
    private void becomeActive()
    {
        recoverAllState();
        _model.SetHAStatus(HAState.ACTIVE);
    }

    /**
     * Invoked on the inputflow when flow INACTIVE event occurs
     */
    private void becomeBackup()
    {
        _inputflow.stop();
        _model.SetHAStatus(HAState.BACKUP);
    }

    private final static String SENTINEL = "SENTINEL";

    private final SolaceConnector _connector;
    private final ClusterModel<InputType,OutputType> _model;
    private final ClusteredAppSerializer<InputType, OutputType> _serializer;
    private String _inputQueueName, _stateQueueName;

    private FlowHandle _stateflow, _inputflow;

    private final ByteBuffer _inbuff  = ByteBuffer.allocate(8192);
    private final ByteBuffer _outbuff = ByteBuffer.allocate(8192);
}
