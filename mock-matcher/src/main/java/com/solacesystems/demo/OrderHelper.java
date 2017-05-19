package com.solacesystems.demo;

import java.util.Random;

class OrderHelper {

    public static ClientOrder nextOrder(int oid) {
        return nextOrder( oid, "MSFT", 100, 0.25 );
    }

    public static ClientOrder nextOrder(int oid, String sym, double par, double delta) {
        ClientOrder order = new ClientOrder( oid );
        order.setIsBuy( _rand.nextBoolean() );
        order.setQuantity( _rand.nextDouble() * 1000);
        if ( order.isBuy() ) {
            order.setPrice( par - (_rand.nextInt(3) * delta) );
        }
        else {
            order.setPrice( par + (_rand.nextInt(3) * delta) );
        }
        order.setInstrument( sym );
        order.setTrader( randTrader() );
        return order;
    }

    public static ClientOrder makeBuy(int oid, String instr, double price, double quantity) {
        ClientOrder order = new ClientOrder( oid );
        order.setIsBuy( true );
        order.setInstrument( instr );
        order.setPrice( price );
        order.setQuantity( quantity );
        return order;
    }
    public static ClientOrder makeSell(int oid, String instr, double price, double quantity) {
        ClientOrder order = new ClientOrder( oid );
        order.setIsBuy( false );
        order.setInstrument( instr );
        order.setPrice( price );
        order.setQuantity( quantity );
        return order;
    }

    public static String randTrader() {
        return TRADERS[ _rand.nextInt(TRADERS.length) ];
    }
    private static final String[] TRADERS = {
            "JPMC", "GS", "MS", "DB", "HSBC", "RBC", "TD", "UBS", "WF"
    };

    private static final Random _rand = new Random();
}
