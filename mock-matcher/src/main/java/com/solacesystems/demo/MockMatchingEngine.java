package com.solacesystems.demo;

import com.solacesystems.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

class MockMatchingEngine implements ClusterEventListener<ClientOrder, MatcherState> {
    private static final Logger logger = LoggerFactory.getLogger(MockMatchingEngine.class);

    public static void main(String[] args) {
        if (args.length < 10) {
            System.out.println("USAGE: <IP> <APP-ID> <APP-INST-#> <SOL-VPN> <SOL-USER> <SOL-PASS> <IN-TOPIC> <STATE-TOPIC> <ACTIVE-TOPIC> <STANDBY-TOPIC>\n\n\n");
            return;
        }
        String host        = args[0];
        String appId       = args[1];
        int instance       = Integer.parseInt(args[2]);
        String vpn         = args[3];
        String user        = args[4];
        String pass        = args[5];
        String inTopic     = args[6];
        String stateTopic  = args[7];
        String activeTopic = args[8];
        String standbyTopic= args[9];

        MockMatchingEngine matcher = new MockMatchingEngine(appId, instance, inTopic, stateTopic, activeTopic, standbyTopic);

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

    public MockMatchingEngine(String appId, int instance, String inTopic, String stateTopic, String activeTopic, String standbyTopic) {
        _inTopic = inTopic;
        _stateTopic = stateTopic;
        _activeTopic = activeTopic;
        _standbyTopic = standbyTopic;
        // State tracking classes; normally wouldn't include all this stuff, but
        // it's useful in the output monitor to show the complete state of all members
        _state = new MatcherState();
        _state.setMatcher( new Matcher( 100, 0.25 ) );
        _state.setApp( appId );
        _state.setInstance( instance );
        _state.setInstrument( "AAPL" ); // TODO FIX
        // Underlying cluster model and message-bus connector
        _model = new ClusterModel<ClientOrder, MatcherState>( this );
        _connector = new ClusterConnector<ClientOrder, MatcherState>( _model, new MockMatchingEngineSerializer() );
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
        _connector.SendOutput(_activeTopic, _standbyTopic, _state);
        sendTradeAnnouncements( trades );

        return _state;
    }

    //// As Backup, we receive the state output from the Primary
    public void OnStateMessage(MatcherState state) {
        // IMPORTANT: This is an event read from our State Queue when we are BACKUP;
        // this is the latest output from the ACTIVE member, so we should update our
        // Matching Engine state with this data to keep in sync
        if (state != null) {
            _state.setHAStatus( _model.GetHAStatus() );
            _state.setSeqStatus( _model.GetSequenceStatus() );
            _state.setLastInput( state.getLastInput() );
            _state.setLastOutput( state.getLastInput() );
            _state.setMatcher( state.getMatcher() );
        }
        sendMonitorUpdate();
    }

    private void sendMonitorUpdate() {
        if (_model.GetHAStatus() != HAState.DISCONNECTED) {
            logger.debug("Sending monitor update with HA Status {}", _model.GetHAStatus());
            _connector.SendOutput(_activeTopic, _standbyTopic, _state);
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

    private final ClusterModel<ClientOrder,MatcherState> _model;
    private final ClusterConnector<ClientOrder,MatcherState> _connector;
    private final MatcherState _state;
    private final ByteBuffer _sndbuf = ByteBuffer.allocate(256);

    private final String _inTopic;
    private final String _activeTopic;
    private final String _standbyTopic;
    private final String _stateTopic;
}
