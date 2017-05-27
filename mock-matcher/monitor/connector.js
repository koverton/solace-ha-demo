//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Connectivity Info
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//// My local VMR
var SOLACE_URL  = 'ws://192.168.56.151'
var SOLACE_VPN  = 'ha_demo'
var SOLACE_USER = 'monitor'
var SOLACE_PASS = 'monitor'
//// My DataGo Service
// var SOLACE_URL  = 'ws://msgvpn-3419.messaging.datago.io:20131'
// var SOLACE_VPN  = 'msgvpn-3419'
// var SOLACE_USER = 'datago-client-username'
// var SOLACE_PASS = '72f9nie8jpfdaj8gjse23vr21t'

//// App-specific stuff
var APPID       = 'app1'
var ACTIVE_SUB  = 'active_matcher/'  + APPID + '/>'
var STANDBY_SUB = 'standby_matcher/' + APPID + '/>'
var TRADE_SUB   = 'trade/'           + APPID + '/>'
var DISCONN_SUB = '#LOG/INFO/CLIENT/*/CLIENT_CLIENT_DISCONNECT/>'

//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//       Solace code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

var sess = null // The Solace session instance


//  Initialize solace session and connect
//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
function initSolaceConn() {
  var factoryProps = new solace.SolclientFactoryProperties()
  factoryProps.logLevel = solace.LogLevel.INFO
  solace.SolclientFactory.init(factoryProps)
  var props = new solace.SessionProperties()
  props.url      = SOLACE_URL
  props.vpnName  = SOLACE_VPN
  props.userName = SOLACE_USER
  props.password = SOLACE_PASS
  try {
    sess = solace.SolclientFactory.createSession(props,
            new solace.MessageRxCBInfo(message_cb, data),
            new solace.SessionEventCBInfo(session_cb, data))
    sess.connect()
  }
  catch(error) {
    console.log(error)
  }
}


// Solace session event callback
//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
function session_cb(sess, evt, userobj) {
  // Wait until the session is UP before subscribing
  if (evt.sessionEventCode == solace.SessionEventCode.UP_NOTICE) {
    // Listen to updates from the active process
    var topic = solace.SolclientFactory.createTopic(ACTIVE_SUB)
    sess.subscribe(topic, true, 'active', 3000)
    // Listen to updates from the standby process
    topic = solace.SolclientFactory.createTopic(STANDBY_SUB)
    sess.subscribe(topic, true, 'standby', 3000)
    // Listen for trade announcements from the active matcher
    topic = solace.SolclientFactory.createTopic(TRADE_SUB)
    sess.subscribe(topic, true, 'trades', 3000)
    // Listen to disconnect/connect events
    topic = solace.SolclientFactory.createTopic(DISCONN_SUB)
    sess.subscribe(topic, true, 'monitor', 3000)
  }
  // Reconnect if we've disconnected
  else if (evt.sessionEventCode == solace.SessionEventCode.DISCONNECTED) {
    console.log('CONNECTION LOST; RETRYING...')
    sess.connect()
  }
}

// Solace inbound message callback
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function message_cb(sess, msg, uo, unused) {
  // UPDATE all displays!
  var payload = msg.getBinaryAttachment()
  var topic   = msg.getDestination().getName()
  if ( !onDisconnect(topic, payload) ) {
      if ( !onTrade(topic, payload) ) {
        if (onMatcherStatus(topic, payload))
            clearTradeAnnounces()
      }
  }
}
