package com.solacesystems.ha.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stores all the state relevant to the cluster member instance include HA state,
 * Sequencing state, and last input/output state messages
 *
 * The cluster model also updates a ClusterEventListener on all state changes.
 *
 * @param <InputType> -- input message type
 * @param <OutputType>-- output message type
 */
public class ClusterModel<InputType, OutputType> {
    private static final Logger logger = LoggerFactory.getLogger(ClusterModel.class);

    public ClusterModel(ClusterEventListener<InputType,OutputType> listener) {
        _listener = listener;
        _haStatus = HAState.DISCONNECTED;
        _seqStatus= SeqState.INIT;
    }

    public HAState GetHAStatus() {
        return _haStatus;
    }
    public void SetHAStatus(HAState haStatus) {
        if (haStatus.equals(_haStatus)) return;
        HAState old = _haStatus;
        _haStatus = haStatus;
        _listener.OnHAStateChange(old, haStatus);
    }

    public SeqState GetSequenceStatus() {
        return _seqStatus;
    }
    public void SetSequenceStatus(SeqState seqStatus) {
        if (seqStatus.equals(_seqStatus)) return;
        SeqState old = _seqStatus;
        _seqStatus = seqStatus;
        _listener.OnSeqStateChange(old, seqStatus);
    }

    public InputType GetLastInput() {
        return _lastInput;
    }

    public OutputType GetLastOutput() {
        return _lastOutput;
    }

    public void SetLastOutput(OutputType lastOutput) {
        _listener.OnStateMessage(lastOutput);
        _lastOutput = lastOutput;
    }

    /**
     * This is an important variation of SetLastInput where the
     * ClusterConnector knows that the cluster instance is LIVE and UP_TO_DATE,
     * so every input requires an updated state output
     *
     * @param input -- the input message driving a potential application state change
     */
    public void UpdateApplicationState(InputType input) {
        _lastOutput = _listener.UpdateApplicationState(input);
        _lastInput  = input;
    }

    @Override
    public String toString() {
        return  "] HA = ["  + _haStatus +
                "] SEQ = [" + _seqStatus +
                "] IN = ["  + stringOrNull(_lastInput) +
                "] OUT = [" + stringOrNull(_lastOutput) + "]";
    }

    private static String stringOrNull(Object obj) {
        if (obj == null) return "(null)";
        return obj.toString();
    }

    private InputType  _lastInput;
    private OutputType _lastOutput;
    private HAState    _haStatus;
    private SeqState   _seqStatus;

    private final ClusterEventListener<InputType,OutputType> _listener;
}
