//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Monitor Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

function init() {
  initMatcher()
  initLadder()
  initSolaceConn()
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
    else if (among(value, ['BACKUP', 'RECOVERING']))
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

