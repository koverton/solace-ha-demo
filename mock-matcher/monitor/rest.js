var data = [ ]

//// Google compute VMR
var google_props = {
    url  : 'ws://104.155.140.201',
    vpn  : 'ha_demo',
    user : 'monitor',
    pass : 'monitor'
}
//// My DataGo Service
var rest_topics = { }

//// Invoked by html->body:onload
function initRest() {
  console.log('Initializing for rest')
  initSolaceConn(google_props, rest_topics)
}


// Invoked by various buttons for each instance;
// depending on the current record state may request
// either starting or stopping of the instance
function procCtl(instance) {
    var fldname = 'ctl' + instance
    var field = document.getElementById(fldname)
    var sendvalue = field.value
    sendRequest('rest/control/request', sendvalue, replyfunc, failfunc)
}


function replyfunc(sess, msg, uo, unused) {
console.log('REPLY!!!!')
  var payload = msg.getBinaryAttachment()
  var topic   = msg.getDestination().getName()
  console.log(payload)
  document.getElementById('rest_response').innerHTML += '<p>' + payload + '</p>'
}

function failfunc(sess, evt, uo, unused) {
  console.log('ReqRep ERROR!!!!' + JSON.stringify(evt))
}
