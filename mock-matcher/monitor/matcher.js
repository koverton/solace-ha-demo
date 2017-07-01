//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Monitor Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

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

function getRecord(index) {
    return data[index]
}

function updateRecord(record, upd) {
    record.haStatus = upd.haStatus
    record.seqStatus = upd.seqStatus
    record.lastInput = upd.lastInput
    record.lastOutput = upd.lastOutput
}

function clearRecord(inst) {
        if (inst <= data.length) {
          var record = getRecord(inst-1)
          updateRecord(record,
            {
                 instance: inst,
                 haStatus: 'Init',
                 seqStatus: 'Disconnected',
                 lastInput: '(null)',
                 lastOutput: '(null)',
            })
          updateMatcherUI(record)
        }
}


//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
// UI Interaction
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

function initMatcher() {
  updateMatcherUI(data[0])
  updateMatcherUI(data[1])
}

function updateMatcherUI(record) {
    setFieldValue('haStatus'  + record.instance, record.haStatus)
    setFieldValue('seqStatus' + record.instance, record.seqStatus)
    setFieldValue('lastInput' + record.instance, gt0(record.lastInput))
    setFieldValue('lastOutput'+ record.instance, gt0(record.lastOutput))
    updateCtlButton(record)
}
function gt0(value) {
  if (value > 0) return value
  return '-'
}

function btnStyle(fld, txt, ttl, clr) {
  fld.innerHTML = txt
  fld.title = ttl
  fld.style.backgroundColor = clr
}
function updateCtlButton(record) {
    var id = 'ctl'+record.instance
    var field = document.getElementById(id)
    if (field == null) {
        console.log('NO SUCH FIELD AS ' + id)
        return
    }
    field.disabled  = false
    if (record.instance == 'ogw') {
      if (record.running) {
        btnStyle(field, 'X', 'Click to Stop', 'red')
      }
      else {
        btnStyle(field, 'O', 'Click to Start', 'green')
        document.getElementById('ogw_in').innerHTML = '' // &darr;
      }
    }
    else if (record.seqStatus == 'Disconnected')
        btnStyle(field, 'O', 'Click to Start', 'green')
    else
        btnStyle(field, 'X', 'Click to Stop', 'red')
}

//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
// Messaging Event Interaction
//  - + - + - + - + - + - + - + - + - + - + - + - + - + -

function onMatcherStatus(topic, payload) {
    // MATCHER UPDATE
    var update = JSON.parse(payload)
    var record = getRecord(update.instance-1)
    updateRecord(record, update)
    updateMatcherUI(record)
    // If the record came from an ACTIVE member, use it's data to update our order stack
    if (update.haStatus == 'ACTIVE') {
        //console.log('New update from ' + upd.instance + ' State: ' + upd.haStatus)
        var stack = update.data
        if (null != stack && '' != stack) {
            updateLadder(stack)
        }
        return true // means 'handled'
    }
    return false // means 'not handled'
}

function onDisconnect(topic, payload) {
  if ( -1 != topic.search('CLIENT_CLIENT_DISCONNECT') ) {
    // DISCONNECT EVENT
    // Handle only disconnects from any HA member; disregard all other disconnects
    var match = topic.match('[0-9]$')
    if (null != match)
        clearRecord(match[0])
    return true // means 'handled'
  }
  return false // means 'not handled'
}

function onOrderEvent(topic, payload) {
  if ( topic == 'order/new' ) {
    updateCtlButton({ instance: 'ogw', running: true })
    document.getElementById('ogw_in').innerHTML = '&darr;'
    return true // means 'handled'
  }
  return false // means 'not handled'
}
