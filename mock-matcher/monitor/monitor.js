//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Monitor Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
var SOLACE_URL  = 'ws://192.168.56.151'
var SOLACE_VPN  = 'ha_demo'
var SOLACE_USER = 'monitor'
var SOLACE_PASS = 'monitor'
var APPID       = 'app1'

var ACTIVE_SUB  = 'active_matcher/' + APPID + '/>'
var STANDBY_SUB = 'standby_matcher/' + APPID + '/>'
var TRADE_SUB   = 'trade/' + APPID + '/>'
var DISCONN_SUB = '#LOG/INFO/CLIENT/*/CLIENT_CLIENT_DISCONNECT/>'

// Initial data
var data =
[
    {
      instance: 1,
      haStatus: 'Init',
      seqStatus: 'Disconnected',
      lastInput: '(null)',
      lastOutput: '(null)',
    },
    {
      instance: 2,
      haStatus: 'Init',
      seqStatus: 'Disconnected',
      lastInput: '(null)',
      lastOutput: '(null)',
    }
]

//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
// UI Interaction -- initialize UI & Solace
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function init() {
  updateUI(data[0])
  updateUI(data[1])
  initSolaceConn()
}

function setFieldValue(name, value) {
    var field = document.getElementById(name)
    if (field == null) {
      console.log('could not find document field named ' + name )
    }
    field.value = value
}

function gt0(value) {
  if (value > 0) return value;
  return '-'
}
function updateUI(record) {
    setFieldValue('haStatus' + record.instance, record.haStatus)
    setFieldValue('seqStatus' + record.instance, record.seqStatus)
    setFieldValue('lastInput' + record.instance, gt0(record.lastInput))
    setFieldValue('lastOutput' + record.instance, gt0(record.lastOutput))
}
function updateRec(record, upd) {
    record.haStatus = upd.haStatus
    record.seqStatus = upd.seqStatus
    record.lastInput = upd.lastInput
    record.lastOutput = upd.lastOutput
}

function nowstamp() {
    var now = new Date()
    return now.toTimeString()
}

function announceTrade(trade) {
    var table = document.getElementById("grid")
    var row = table.rows.namedItem('trade_announce')
    if (row == null) {
        console.log('ERROR: Could not find trade_announce row')
    }
    else {
        row.cells[0].innerHTML = 'New Trade @' + trade.price + ' - ' +  nowstamp()
    }
}
function clearTradeAnnounces() {
    table.rows.namedItem('trade_announce').cells[0].innerHTML = ''
}

function setupNewCell(cell, value, className) {
    cell.className = className
    cell.innerHTML = value
}
function initLadder(table, par, inc) {
    var tlen = table.rows.length
    for( var i = 5; i >= -5; i--) {
        price = par + (inc * i)
        var row = table.rows.namedItem(price)
        if (null == row) {
            var row = table.insertRow(tlen++)
            row.id = price
            setupNewCell( row.insertCell(0), '', 'buy' )
            setupNewCell( row.insertCell(1), price.toFixed(2), 'price' )
            setupNewCell( row.insertCell(2), '', 'sell' )
        }
    }
}

function updateLadder(stack) {
    var table = document.getElementById("grid")
    initLadder( table, stack.par, stack.priceInc )
    // update sells
    var sells= stack.sells
    var slen = sells.length
    for(var i = 0; i < slen; i++) {
        var level = sells[i]
        var row = table.rows.namedItem(level.price)
        if (null != row) {
            //console.log('Updating sell @' + level.price)
            if (level.quantity == 0)
                row.cells[2].innerHTML = ''
            else
                row.cells[2].innerHTML = level.quantity.toFixed(2)
        }
    }
    // update buys
    var buys= stack.buys
    var blen = buys.length
    for(var i = 0; i < blen; i++) {
        var level = buys[i]
        var row = table.rows.namedItem(level.price)
        if (null != row) {
            //console.log('Updating buy @' + level.price)
            if (level.quantity ==  0)
                row.cells[0].innerHTML = ''
            else
                row.cells[0].innerHTML = level.quantity.toFixed(2)
        }
    }
}

function setVisible(id, isVisible) {
    document.getElementById(id).style.visibility = 
            (isVisible ? 'visible' : 'hidden')
}

//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//       Solace code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

var sess = null // The Solace session instance

// Solace inbound message callback
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function message_cb(sess, msg, uo, unused) {
  // UPDATE Monitor display!
  var payload = msg.getBinaryAttachment()
  var topic   = msg.getDestination().getName()
  if ( -1 == topic.search('CLIENT_CLIENT_DISCONNECT') ) {
      if ( -1 == topic.search('trade/' + APPID) ) {
        // REAL matcher updates, not trades or disconnects
          var upd    = JSON.parse(payload)
          var record = data[upd.instance-1]
          updateRec(record, upd)
          updateUI(record)
          // If the record came from an ACTIVE member, use it's data to update our order stack
          if (upd.haStatus == 'ACTIVE') {
             //console.log('New update from ' + upd.instance + ' State: ' + upd.haStatus)
             var stack = upd.data
             if (null != stack && '' != stack) {
                 updateLadder(stack)
             }
          }
          //clearTradeAnnounces()
      }
      else {
        //Trade announcements
          var trade = JSON.parse(payload)
          console.log('TRADE: ' + payload)
          announceTrade(trade)
      }
  }
  else {
    //#LOG/INFO/CLIENT/{appliance-name}/CLIENT_CLIENT_DISCONNECT/{vpn-name}/{client-name}
    // Handle disconnect of any HA member; disregard all other disconnects
    var m = topic.match('[0-9]$')
    if (null == m) return
    var inst = m[0]
    if (inst <= data.length) {
      var rec = {
        instance: inst,
        haStatus: 'Init',
        seqStatus: 'Disconnected',
        lastInput: '(null)',
        lastOutput: '(null)',
      }
      var record = data[inst-1]
      updateRec(record, rec)
      updateUI(record)
    }
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
