//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Monitor Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
var APPID       = 'app1'

function init() {
  initMatcher()
  initLadder()
  initSolaceConn()
}

function procCtl(instance) {
    var field = document.getElementById('ctl'+instance)
    field.style.backgroundColor = 'gray'
    field.disabled  = true
    field.innerHTML = '-'
    var op = 'none'
    var rec = getRecord(instance-1)
    if (rec.seqStatus == 'Disconnected')
        op = 'start'
    else
        op = 'kill'
    var topic = APPID + '/control/' + op + '/' + instance
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

