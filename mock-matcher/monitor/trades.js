//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Trade Display Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
var APPID       = 'aaplmatcher'

function initTrades() {
  addMsgHandler(onTrade)
}

function onTrade(topic, payload) {
  var update = JSON.parse(payload)
  if ( -1 != topic.search('trade/' + APPID) ) {
    //TRADE EVENT
      announceTrade(update)
      return true // means 'finished'
  }
  return false // means 'not finished'
}

function announceTrade(trade) {
console.log('ANNOUNCING TRADE ' + JSON.stringify(trade))
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
console.log('CLEARING TRADES')
    var table = document.getElementById("grid")
    var tradeRow = table.rows.namedItem('trade_announce')
    if (tradeRow != null) 
        tradeRow.cells[0].innerHTML = ''
}

