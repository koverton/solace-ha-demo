package com.solacesystems.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

class Matcher {
    private static final Logger logger = LoggerFactory.getLogger(Matcher.class);

    public static class Lvl {
        public Lvl(final ClientOrder order) {
            price = order.getPrice();
            quantity = order.getQuantity();
        }
        public Lvl(double px, double qty) {
            price = px;
            quantity = qty;
        }

        public double getPrice() {
            return price;
        }
        public double getQuantity() {
            return quantity;
        }
        public void addQuantity(double q) {
            quantity += q;
        }
        public void setQuantity(double q) {
            quantity = q;
        }
        private final double price;
        private double quantity;
    }

    private Matcher() {
        throw new InvalidParameterException();
    }

    public Matcher(double par, double pxIncrement) {
        this.par = par;
        this.pxIncrement = pxIncrement;
    }

    public double getPar() { return this.par; }

    public double getPriceIncrement() {
        return this.pxIncrement;
    }

    public List<Lvl> getBuys() {
        return buys;
    }

    public List<Lvl> getSells() {
        return sells;
    }

    public double minPrice() {
        if (buys.size() == 0) {
            if (sells.size() == 0)
                return Double.NaN;
            return sells.get(0).getPrice();
        }
        return buys.get( buys.size()-1 ).getPrice();
    }
    public double maxPrice() {
        if (sells.size() == 0) {
            if (buys.size() == 0)
                return Double.NaN;
            return buys.get(0).getPrice();
        }
        return sells.get( sells.size()-1 ).getPrice();
    }

    public List<Trade> addOrder(ClientOrder order) {
        if (order.isBuy()) {
            int blen = buys.size();
            for(int b = 0; b < blen; b++) {
                Lvl buy = buys.get(b);
                // highest price at the middle (top of the stack)
                if (order.getPrice() == buy.getPrice()) {
                    buy.addQuantity((int)order.getQuantity());
                    return findSellMatches( buy, b );
                }
                else if (order.getPrice() > buy.getPrice()) {
                    Lvl newBuy = new Lvl(order);
                    buys.add(b, newBuy);
                    return findSellMatches( buy, b );
                }
            }
            buys.add(new Lvl(order));
        }
        else {
            int slen = sells.size();
            for(int s = 0; s < slen; s++) {
                Lvl sell = sells.get(s);
                // lowest price at the middle (bottom of the stack)
                if (order.getPrice() == sell.getPrice()) {
                    sell.addQuantity((int)order.getQuantity());
                    return findBuyMatches( sell, s );
                }
                else if (order.getPrice() < sell.getPrice()) {
                    Lvl newSell = new Lvl(order);
                    sells.add(s, newSell);
                    return findBuyMatches( sell, s );
                }
            }
            sells.add(new Lvl(order));
        }
        return null;
    }

    private List<Trade> findSellMatches(Lvl buy, int b) {
        List<Trade> trades = new ArrayList<Trade>();
        for(int s = 0; s < sells.size(); s++) {
            Lvl sell = sells.get(s);
            if ( sell.getPrice() > buy.getPrice() )
                return trades;
            Trade trade = trade( buy, b, sell, s );
            trades.add( trade );
        }
        return trades;
    }

    private List<Trade> findBuyMatches(Lvl sell, int s) {
        List<Trade> trades = new ArrayList<Trade>();
        for(int b = 0; b < buys.size(); b++) {
            Lvl buy = buys.get(b);
            if ( sell.getPrice() > buy.getPrice() )
                return trades;
            Trade trade = trade( buy, b, sell, s );
            trades.add( trade );
        }
        return trades;
    }

    private Trade trade(Lvl buy, int b, Lvl sell, int s) {
        Trade trade = new Trade();
        trade.setPrice( avgPx(buy, sell) );
        if (sell.getQuantity() > buy.getQuantity() ) {
            // Create the trade record
            trade.setQuantity( buy.getQuantity() );
            logger.info("TRADE: {} @ {}", trade.getQuantity(), trade.getPrice() );
            // deduct the amount bought from the sell inventory
            sell.addQuantity( (-1)*buy.getQuantity() );
            logger.info("NEW SELL QTY: {}", sell.getQuantity() );
            // the buy side is wiped out, but we want to generate a 0-quantity event for listeners;
            // after generating the update we'll clear empty levels out
            buy.setQuantity( 0 );
            logger.info("CLEARING BUY QTY AT: {}", buy.getPrice() );
        }
        else {
            // Create the trade record
            trade.setQuantity( sell.getQuantity() );
            logger.info("TRADE: {} @ {}", trade.getQuantity(), trade.getPrice() );
            // deduct the amount sold from the buy inventory
            buy.addQuantity( (-1)*sell.getQuantity() );
            logger.info("NEW BUY QTY: {}", buy.getQuantity() );
            // the sell side is wiped out, but we want to generate a 0-quantity event for listeners;
            // after generating the update we'll clear empty levels out
            sell.setQuantity( 0 );
            logger.info("CLEARING SELL QTY AT: {}", sell.getPrice() );
            // if our buy quantity is 0, clear it too
            if ( 0 >= buy.getQuantity() ) {
                // buys.remove( b );
                logger.info("CLEARING BUY QTY AT: {}", buy.getPrice() );
            }
        }
        return trade;
    }

    private double avgPx(Lvl b, Lvl s) {
        return (b.getPrice() + s.getPrice()) / 2.0;
    }

    private final double par;
    private final double pxIncrement;
    private final List<Lvl> buys = new ArrayList<Lvl>();
    private final List<Lvl> sells = new ArrayList<Lvl>();
}
