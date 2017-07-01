//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Trade Display Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
var APPID       = 'app1'

function onTrade(topic, payload) {
  if ( -1 != topic.search('trade/' + APPID) ) {
    //TRADE EVENT
      announceTrade(JSON.parse(payload))
      return true // means 'handled'
  }
  return false // means 'not handled'
}

function announceTrade(trade) {
    var table = document.getElementById("grid")
    var row = table.rows.namedItem('trade_announce')
    if (row == null) {
        console.log('ERROR: Could not find trade_announce row')
    }
    else {
        row.cells[0].innerHTML = 'New Trade @'
                + trade.price + ' - '
                +  new Date().toTimeString()
    }
}

function clearTradeAnnounces() {
    var table = document.getElementById("grid")
    table.rows.namedItem('trade_announce').cells[0].innerHTML = ''
}

