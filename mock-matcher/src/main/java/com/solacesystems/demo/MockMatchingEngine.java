package com.solacesystems.demo;

import com.solacesystems.ha.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

class MockMatchingEngine implements ClusterEventListener<ClientOrder, MatcherState> {
    private static final Logger logger = LoggerFactory.getLogger(MockMatchingEngine.class);

    public static void main(String[] args) {
        if (args.length < 12) {
            System.out.println("USAGE: MockMatchingEngine <HOST> <SOL-VPN> <SOL-USER> <SOL-PASS> <APP-ID> <APP-INST-#> <IN-TOPIC> <STATE-TOPIC> <ACTIVE-TOPIC> <STANDBY-TOPIC>\n\n\n");
            return;
        }
        String host        = args[0];
        String vpn         = args[1];
        String user        = args[2];
        String pass        = args[3];
        String appId       = args[4];
        int instance       = Integer.parseInt(args[5]);
        String inTopic     = args[6];
        String stateTopic  = args[7];
        String activeTopic = args[8];
        String standbyTopic= args[9];
        String instrument  = args[10];
        double initialPar  = Double.parseDouble(args[11]);

        MockMatchingEngine matcher = new MockMatchingEngine(appId, instance, inTopic, stateTopic, activeTopic, standbyTopic, instrument, initialPar);

        matcher.Connect(host, vpn, user, pass);

        // That's it; either do other work while waiting for events, or run this loop below...

        boolean running = true;
        while (running)
        {
            try {
                Thread.sleep(1000);
            } catch(InterruptedException e) {
                e.printStackTrace();
                running = false;
            }
        }
    }

    public MockMatchingEngine(String appId, int instance, String inTopic, String stateTopic, String activeTopic, String standbyTopic, String instrument, double par) {
        _inTopic = inTopic;
        _stateTopic = stateTopic;
        _activeTopic = activeTopic;
        _standbyTopic = standbyTopic;
        // State tracking classes; normally wouldn't include all this stuff, but
        // it's useful in the output monitor to show the complete state of all members
        _state = new MatcherState( appId, instance, instrument );
        _state.setMatcher( new Matcher( par, 0.25 ) );
        // Underlying cluster model and message-bus connector
        _serializer = new MockMatchingEngineSerializer();
        _connector = new ClusterConnector<ClientOrder, MatcherState>( this, _serializer);

        _timer = new Timer();
        _lastTs = System.currentTimeMillis();
        _timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                periodicStatusUpdate();
            }
        }, 1000, 1000);
    }

    public void Connect(String host, String vpn, String user, String pass) {
        _connector.Connect(host, vpn, user, pass, _state.getApp()+"_inst"+_state.getInstance());

        String inputQueue = _state.getApp() + "_input";
        String stateQueue = _state.getApp() + "_state" + _state.getInstance();

        _connector.BindQueues(inputQueue, _inTopic, stateQueue, _stateTopic);
    }



    public void OnHAStateChange(HAState oldState, HAState newState) {
        logger.info("HA Change: {} => {}", oldState, newState);
        _state.setHAStatus(newState);
        sendMonitorUpdate();
    }

    public void OnSeqStateChange(SeqState oldState, SeqState newState) {
        logger.info("Seq Change: {} => {}", oldState, newState);
        _state.setSeqStatus(newState);
        sendMonitorUpdate();
    }

    //// As the Primary, we receive inputs to the application
    public MatcherState UpdateApplicationState(ClientOrder input) {
        // IMPORTANT: A State change while we're up-to-date, so every input
        // represents real application changes we need to represent

        // Track results of new orders
        List<Trade> trades = _state.addOrder(input);
        _connector.SendOutput(_activeTopic, _state);
        sendTradeAnnouncements( trades );

        return _state;
    }

    //// As Backup, we receive the state output from the Primary
    public void OnStateMessage(MatcherState state) {
        // IMPORTANT: This is an event read from our State Queue when we are BACKUP;
        // this is the latest output from the ACTIVE member, so we should update our
        // Matching Engine state with this data to keep in sync
        if (state != null) {
            // This is the real application work, tracking state
            _state.setMatcher( state.getMatcher() );
            // This is an extra bit added for the demo so we can externalize the whole
            // HA state for visualization outside the app
            _state.setHAStatus( _connector.getModel().GetHAStatus() );
            _state.setSeqStatus( _connector.getModel().GetSequenceStatus() );
            _state.setLastInput( state.getLastInput() );
            _state.setLastOutput( state.getLastInput() );
        }
        sendMonitorUpdate();
    }

    /**
     * Not strictly needed for the real-world HA app, this is an extra bit I added to better
     * externalize/visualize the application state when in BACKUP mode. BACKUPs don't need to
     * send any output.
     */
    private void sendMonitorUpdate() {
        if (_connector != null && _connector.getModel() != null) {
            HAState current = _connector.getModel().GetHAStatus();
            if (current != HAState.DISCONNECTED) {
                logger.debug("Sending monitor update with HA Status {}", current);
                _connector.SendOutput(_activeTopic, _state);
                _connector.SendSerializedOutput(_standbyTopic, _serializer.SerializeOutput(_state));
            }
            _lastTs = System.currentTimeMillis();
        }
    }

    private void sendTradeAnnouncements(List<Trade> trades) {
        if (null == trades) return;
        for( Trade trade : trades) {
            _sndbuf.clear();
            String jsonstr = JSONSerializer.SerializeTrade(trade).toJSONString();
            _sndbuf.put(jsonstr.getBytes());
            _connector.SendSerializedOutput( "trade/"+_state.getApp()+"/new", _sndbuf );
        }
    }

    private void periodicStatusUpdate() {
        long newTs = System.currentTimeMillis();
        if (999 < (newTs - _lastTs))
            sendMonitorUpdate();
        else
            _lastTs = newTs;
    }

    private final ClusterConnector<ClientOrder,MatcherState> _connector;
    private final MockMatchingEngineSerializer _serializer;
    private final MatcherState _state;
    private final ByteBuffer _sndbuf = ByteBuffer.allocate(256);

    private final String _inTopic;
    private final String _activeTopic;
    private final String _standbyTopic;
    private final String _stateTopic;

    private final Timer _timer;
    private long _lastTs;
}
