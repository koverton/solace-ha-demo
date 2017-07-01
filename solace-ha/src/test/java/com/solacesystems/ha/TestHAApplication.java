package com.solacesystems.ha;

import com.solacesystems.ha.model.*;

/**
 * Sample HA Application using the com.solacesystems.ha library:
 *
 * Owns: ClusterConnector<InputType,OutputType> which connects to the Solace bus and manages all
 *     data transfer, sync, and HA state mgmt via the ClusterModel
 *
 * Implements: ClusterEventListener<InputType,OutputType>
 *     which is updated by it's owner ClusterModel<InputType,Output> on all HA-application events
 *
 * Important ClusterEvents to follow:
 *
 *     OnHAStateChange(old, new): when this instance changes it's ACTIVE or BACKUP role it's behavior should change.
 *         As the ACTIVE member, it is expected to process messages of InputType and send result messages of OutputType.
 *         As the BACKUP member, it is expected to process messages of OutputType from it's ACTIVE peer and update it's internal state.
 *
 *     OnSeqStateChange(old, new): when this instance changes it's status with respect to it's place in the overall
 *         cluster's input stream. An UP_TO_DATE event indicates to an ACTIVE member that it has accounted for all
 *         inputs received by the previous ACTIVE member, so any new input messages should result in new output messages.
 *
 *     OnStateMessage(OutputType): invoked on BACKUP members when a new output message was produced by the ACTIVE member.
 *         BACKUP members should update their internal state with the content of this message to remain in sync.
 *
 *     UpdateApplicationState(InputType): invoked on ACTIVE member when a new input message arrives for processing.
 *         This function is where the application does the "real work" of processing the input to derive any valid
 *         outputs which are passed back to the library in the return value (so the ClusterModel may be updated).
 */
class TestHAApplication implements ClusterEventListener<Integer, Double> {
    private ClusterConnector<Integer,Double> _connector;
    private Integer _lastInput;
    private Double  _lastOutput;
    private int     _instance;
    private String  _outputTopic;
    private String  _stateQueue;

    public TestHAApplication(int instance, String outputTopic) {
        _instance    = instance;
        _outputTopic = outputTopic;

        _connector   = new ClusterConnector<Integer, Double>(this, new TestSerializer());
    }

    public void Start(String inputQueueName, String inputTopicName, String stateQueueName, String stateQueueTopic) {
        _stateQueue = stateQueueName;
        _connector.Connect(ConnectionFields.HOST,
                ConnectionFields.VPN,
                ConnectionFields.USER,
                ConnectionFields.PASS,
                "SampleApp" + _instance);
        _connector.BindQueues(inputQueueName, inputTopicName, stateQueueName, stateQueueTopic);
    }

    /** EVENT HANDLING  **/
    public void OnHAStateChange(HAState oldState, HAState newState) {
        System.out.println("From HA State: " + oldState + " to " + newState);
    }

    public void OnSeqStateChange(SeqState oldState, SeqState newState) {
        System.out.println("From Sequence State: " + oldState + " to " + newState);
    }

    public void OnStateMessage(Double state) {
        /**
         * HERE'S THE BACKUP APP'S JOB: track output from the ACTIVE member
         */
        System.out.println("State Payload from Peer: " + state);
        _lastOutput = state;
    }

    public Double UpdateApplicationState(Integer input) {
        /**
         * HERE'S THE REAL APPLICATION WORK: for each Innput, produce an output
         */
        _lastInput = input;
        _lastOutput = 1.1 * _lastInput;
        System.out.println("PROCESSING: " + _lastInput + " => " + _lastOutput);
        _connector.SendOutput(_outputTopic, _lastOutput);
        return _lastOutput;
    }

    public ClusterModel<Integer,Double> getModel() { return _connector.getModel(); }

    public String getStateQueueName() { return _stateQueue; }

    public void Destroy() {
        _connector.Destroy();
    }
}
