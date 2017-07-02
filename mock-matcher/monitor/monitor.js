//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Monitor Variables
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

var APPID = 'app1'

//// My local VMR
//  url  : 'ws://35.184.60.189',
var vmr_props = {
    url  : 'ws://192.168.56.151',
    vpn  : 'ha_demo',
    user : 'monitor',
    pass : 'monitor'
}
//// Google compute VMR
var google_props = {
    url  : 'ws://104.155.140.201',
    vpn  : 'ha_demo',
    user : 'monitor',
    pass : 'monitor'
}
//// My DataGo Service
var datago_props = {
    url  : 'ws://msgvpn-3419.messaging.datago.io:20131',
    vpn  : 'msgvpn-3419',
    user : 'datago-client-username',
    pass : '72f9nie8jpfdaj8gjse23vr21t'
}

var ha_topics = {
    active_sub  : 'active_matcher/'  + APPID + '/>',
    standby_sub : 'standby_matcher/' + APPID + '/>',
    trade_sub   : 'trade/'           + APPID + '/>',
    disconn_sub : '#LOG/INFO/CLIENT/*/CLIENT_CLIENT_DISCONNECT/>',
    order_sub   : 'order/new'
}

//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
//        Monitor Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + -

//// Invoked by html->body:onload
function init() {
  initMatcher()
  initLadder()
  initSolaceConn(google_props, ha_topics)
}

// Invoked by various buttons for each instance;
// depending on the current record state may request
// either starting or stopping of the instance
function procCtl(instance) {
    var fldname = 'ctl' + instance
    var field = document.getElementById(fldname)
    var srv = 'matcher'
    if (instance == 'ogw') {
      var op = (field.innerHTML == 'X') ? 'stop' : 'start'
      var topic = APPID + '/control/ogw/' + op + '/1'
      sendEmpty(topic)
      updateCtlButton({ instance: 'ogw', running: false })
      return
    }
    else if (instance == 'rest') {
      var sendvalue = field.value
      sendRequest(APPID + '/control/rest', APPID + '/response/rest', sendvalue)
      return
    }
    field.style.backgroundColor = 'gray'
    field.disabled  = true
    field.innerHTML = '-'
    var op = 'none'
    var rec = getRecord(instance-1)
    if (rec.seqStatus == 'Disconnected')
        op = 'start'
    else
        op = 'stop'
    var topic = APPID + '/control/' + srv + '/' + op + '/' + instance
    sendEmpty(topic)
}

//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
// UI Interaction Helpers
//  - + - + - + - + - + - + - + - + - + - + - + - + - + -

function setFieldValue(name, value) {
    var field = document.getElementById(name)
    if (field == null) {
      console.log('could not find document field named ' + name )
      return
    }
    setDynamicStyle(field, value)
}

function setDynamicStyle(field, value) {
    field.innerHTML = value
    if (among(value, ['ACTIVE', 'UP_TO_DATE']))
        field.className = 'active'
    else if (among(value, ['BACKUP', 'FOLLOWING']))
        field.className = 'backup'
    else if (among(value, ['Init', 'Disconnected']))
        field.className = 'transitory'
    else
        field.className = 'standard'
}

function among(value, set) {
    for(var i = 0; i < set.length; i++) {
        if (value == set[i]) return true
    }
    return false
}

