//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Ladder Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 

// Initial data

var ldr = null
var px_row = {}

function initLadder() {
  ldr = new fin.Hypergrid('#ladder',
  {
  	data: [ { buy : 0 , price : 0 , sell : 0 } ],
  })
  var DEF_BGCOLOR = 'black'
  var DEF_TXTCOLOR = 'white'
  var HDR_TXTCOLOR = '#666'
  ldr.properties.editable = false
  ldr.properties.columnHeaderBackgroundColor =
    ldr.properties.backgroundColor =
        DEF_BGCOLOR
  ldr.properties.color = DEF_TXTCOLOR
  ldr.properties.columnHeaderColor =
    ldr.behavior.getColumn(1).properties.color =
        HDR_TXTCOLOR
  //ldr.properties.showRowNumbers = false

  Object.defineProperty(ldr.properties, 'backgroundColor', {
    get: function() {
        var c = this.gridCell
        if (null == c) return DEF_BGCOLOR
        var lvl = this.dataRow
        if (c.x == 0) {
            if (lvl.buy && lvl.buy != '' && lvl.buy != 0.0)
                return 'green'
        }
        else if (c.x == 2) {
            if (lvl.sell && lvl.sell != '' && lvl.sell != 0.0)
                return 'red'
        }
        return DEF_BGCOLOR
    }
  });
}

function makeLadder(table, par, inc) {
    var data = []
    for( var i = 5; i >= -5; i--) {
        price = par + (inc * i)
	px_row[ price ] = data.length
	data.push( { 'buy' : '' , 'price': price.toFixed(2), 'sell' : '' } )
    }
    return data
}

function updateLadder(stack) {
    var table = makeLadder( table, stack.par, stack.priceInc )
    // update sells
    var sells= stack.sells
    var slen = sells.length
    for(var i = 0; i < slen; i++) {
        var level = sells[i]
        var rnum = px_row[ level.price ]
        if (null != rnum) {
            if (level.quantity == 0)
                table[rnum].sell = ''
            else 
                table[rnum].sell = level.quantity.toFixed(2)
        }
    }
    // update buys
    var buys= stack.buys
    var blen = buys.length
    for(var i = 0; i < blen; i++) {
        var level = buys[i]
        var rnum = px_row[ level.price ]
        if (null != rnum) {
            if (level.quantity ==  0)
                table[rnum].buy = ''
            else
                table[rnum].buy = level.quantity.toFixed(2)
        }
    }
    ldr.setData( table )
}
