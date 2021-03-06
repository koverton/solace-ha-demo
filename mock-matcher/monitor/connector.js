//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Connectivity Info
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

//// App-specific stuff
var ha_topics = null
var callbacks = [ ]

function addMsgHandler(callback) {
    callbacks.push(callback)
}

//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//       Solace code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

var sess = null // The Solace session instance


//  Initialize solace session and connect
//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
function initSolaceConn(conn_props, topics) {
  ha_topics = topics
  var factoryProps = new solace.SolclientFactoryProperties()
  factoryProps.logLevel = solace.LogLevel.INFO
  solace.SolclientFactory.init(factoryProps)
  var props = new solace.SessionProperties()
  props.url      = conn_props.url
  props.vpnName  = conn_props.vpn
  props.userName = conn_props.user
  props.password = conn_props.pass
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
    if (ha_topics.active_sub != null) {
      var topic = solace.SolclientFactory.createTopic(ha_topics.active_sub)
      sess.subscribe(topic, true, 'active', 3000)
    }
    // Listen to updates from the standby process
    if (ha_topics.standby_sub != null) {
      topic = solace.SolclientFactory.createTopic(ha_topics.standby_sub)
      sess.subscribe(topic, true, 'standby', 3000)
    }
    // Listen for trade announcements from the active matcher
    if (ha_topics.trade_sub != null) {
      topic = solace.SolclientFactory.createTopic(ha_topics.trade_sub)
      sess.subscribe(topic, true, 'trades', 3000)
    }
    // Listen to disconnect/connect events
    if (ha_topics.disconn_sub != null) {
      topic = solace.SolclientFactory.createTopic(ha_topics.disconn_sub)
      sess.subscribe(topic, true, 'monitor', 3000)
    }
    // Listen to order events to know when the OGW is online
    if (ha_topics.order_sub != null) {
      topic = solace.SolclientFactory.createTopic(ha_topics.order_sub)
      sess.subscribe(topic, true, 'orders', 3000)
    }
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
  // UPDATE all displays
  var payload = msg.getBinaryAttachment()
  var topic   = msg.getDestination().getName()
  // punk ass 'chain of responsibility' pattern
  for(var i = 0; i < callbacks.length; i++) {
    var cbfn = callbacks[i]
    if (cbfn(topic, payload)) {
      return
    }
  }
}

function send(topic, data) {
    var msg = solace.SolclientFactory.createMessage()
    msg.setDestination(solace.SolclientFactory.createTopic(topic))
    msg.setDeliveryMode(solace.MessageDeliveryModeType.DIRECT)
    msg.setBinaryAttachment(data)
    try {
        sess.send(msg)
    } catch (err) {
        console.log('Failed to send message: ' + msg.toString())
        console.log(err.toString() + err.Message)
    }
}

function sendRequest(topic, data, replyFunc, failFunc) {
    var msg = solace.SolclientFactory.createMessage()
    msg.setDestination(solace.SolclientFactory.createTopic(topic))
    msg.setDeliveryMode(solace.MessageDeliveryModeType.DIRECT)
    msg.setBinaryAttachment(data)
    try {
        sess.sendRequest(msg, 2000, replyFunc, failFunc, null)
    } catch (err) {
        console.log('Failed to send message')
        console.log(err.toString() + err.Message)
    }
}

function sendEmpty(topic) {
console.log('sending message on topic:'+topic)
    var msg = solace.SolclientFactory.createMessage()
    msg.setDestination(solace.SolclientFactory.createTopic(topic))
    msg.setDeliveryMode(solace.MessageDeliveryModeType.DIRECT)
    try {
        sess.send(msg)
    } catch (err) {
        console.log('Failed to send message: ' + msg.toString())
        console.log(err.toString() + err.Message)
    }
}
