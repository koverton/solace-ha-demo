//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
//        Ladder Code
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 


//  - + - + - + - + - + - + - + - + - + - + - + - + - + -
// UI Interaction -- initialize UI & Solace
//  - + - + - + - + - + - + - + - + - + - + - + - + - + - 
function initLadder() {
}

function makeLadder(table, par, inc) {
    var tlen = table.rows.length
    for( var i = 5; i >= -5; i--) {
        price = par + (inc * i)
        var row = table.rows.namedItem(price)
        if (null == row) {
            var row = table.insertRow(tlen++)
            row.id = price
            setupNewCell( row.insertCell(0), '', (price > par ? '' : 'buy') )
            setupNewCell( row.insertCell(1), price.toFixed(2), 'price' )
            setupNewCell( row.insertCell(2), '', (price < par ? '' : 'sell') )
        }
    }
}

function setupNewCell(cell, value, className) {
    cell.className = className
    cell.innerHTML = value
}

function updateLadder(stack) {
    var table = document.getElementById("grid")
    makeLadder( table, stack.par, stack.priceInc )
    // update sells
    var sells= stack.sells
    var slen = sells.length
    for(var i = 0; i < slen; i++) {
        var level = sells[i]
        var row = table.rows.namedItem(level.price)
        if (null != row) {
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
            if (level.quantity ==  0)
                row.cells[0].innerHTML = ''
            else
                row.cells[0].innerHTML = level.quantity.toFixed(2)
        }
    }
}

