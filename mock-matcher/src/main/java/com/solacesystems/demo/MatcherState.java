package com.solacesystems.demo;

import com.solacesystems.model.HAState;
import com.solacesystems.model.SeqState;

import java.util.List;

/**
 * Example application state with a sequence number
 */
class MatcherState {
    public MatcherState() {
        _haStatus = HAState.DISCONNECTED;
        _seqStatus = SeqState.INIT;
    }

    public List<Trade> addOrder(ClientOrder order) {
        _lastInput = order.getSequenceId();
        _lastOutput = order.getSequenceId();
        return _matcher.addOrder( order );
    }


    public String getApp() {
        return _app;
    }

    public void setApp(String app) {
        _app = app;
    }

    public int getInstance() {
        return _instance;
    }

    public void setInstance(int instance) {
        _instance = instance;
    }

    public void setInstrument(String instrument) {
        _instrument = instrument;
    }

    public String getInstrument() { return _instrument; }

    public HAState getHAStatus() {
        return _haStatus;
    }

    public void setHAStatus(HAState haStatus) {
        this._haStatus = haStatus;
    }

    public SeqState getSeqStatus() {
        return _seqStatus;
    }

    public void setSeqStatus(SeqState seqStatus) {
        this._seqStatus = seqStatus;
    }

    public long getLastInput() {
        return _lastInput;
    }

    public void setLastInput(long lastInput) {
        _lastInput = lastInput;
    }

    public long getLastOutput() {
        return _lastOutput;
    }

    public void setLastOutput(long lastOutput) {
        _lastOutput = lastOutput;
    }

    public Matcher getMatcher() {
        return _matcher;
    }

    public void setMatcher(Matcher matcher) {
        _matcher = matcher;
    }


    @Override
    public String toString() {
        return "MatcherState{" +
                "='" + _instrument + '\'' +
                ", seqID=" + _lastInput +
                '}';
    }

    private String _app;
    private int _instance;
    private String _instrument;
    private Matcher _matcher;
    private HAState _haStatus;
    private SeqState _seqStatus;
    private long _lastInput;
    private long _lastOutput;
}
