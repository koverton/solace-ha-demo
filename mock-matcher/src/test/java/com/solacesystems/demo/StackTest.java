package com.solacesystems.demo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StackTest {
    @Test
    public void testEmpty() {
        Matcher matcher = new Matcher( 100,0.25);
        assertEquals(matcher.getBuys().size(), matcher.getSells().size());
        assertEquals(Double.NaN, matcher.maxPrice(), 0.0001);
        assertEquals(Double.NaN, matcher.minPrice(), 0.0001);
    }

    @Test
    public void correctSides() {
        Matcher matcher = new Matcher( 100, 0.25);
        int oid = 1;
        int max_buys = 5;
        for(int i = 0; i < max_buys; i++) {
            ClientOrder order = OrderHelper.nextOrder(oid++);
            order.setIsBuy(true);
            order.setPrice(101.5 + i);
            matcher.addOrder(order);
        }
        assertEquals(max_buys, matcher.getBuys().size());

        int max_sells = 3;
        for(int i = 0; i < max_sells; i++) {
            ClientOrder order = OrderHelper.nextOrder(oid++);
            order.setIsBuy(false);
            order.setPrice(102.5 + i);
            matcher.addOrder(order);
        }
        assertEquals(max_sells, matcher.getSells().size());
        assertEquals(max_buys, matcher.getBuys().size());
    }

    @Test
    public void quantityAggregation() {
        Matcher matcher = new Matcher( 100, 0.25);
        int oid = 1;
        int max_buys = 5;
        int max_sells = 3;
        int quantity = 100;
        for(int i = 0; i < max_buys; i++) {
            ClientOrder order = OrderHelper.nextOrder(oid++);
            order.setIsBuy(true);
            order.setPrice(1.2345);
            order.setQuantity(quantity);
            matcher.addOrder(order);
            assertEquals((i+1)*quantity, matcher.getBuys().get(0).getQuantity(), .001 );
            assertEquals(1.2345, matcher.minPrice(), 0.00001);
        }
        assertEquals(max_buys*quantity, matcher.getBuys().get(0).getQuantity(), .001 );
        for(int i = 0; i < max_sells; i++) {
            ClientOrder order = OrderHelper.nextOrder(oid++);
            order.setIsBuy(false);
            order.setPrice(5.4321);
            order.setQuantity(quantity);
            matcher.addOrder(order);
            assertEquals((i+1)*quantity, matcher.getSells().get(0).getQuantity(), .001 );
        }
        assertEquals(max_sells*quantity, matcher.getSells().get(0).getQuantity(), .001 );
    }

    @Test
    public void buyOrdering() {
        Matcher matcher = new Matcher( 100, 0.25);
        // Highest BID is at the top
        int oid = 1;
        int max_buys = 5;
        double startPx = 123.45;
        double pxIncrement = 2.2;
        for(int i = 0; i < max_buys; i++) {
            ClientOrder order = OrderHelper.nextOrder(oid++);
            order.setIsBuy(true);
            order.setPrice(startPx + (i * pxIncrement));
            order.setQuantity(100);
            matcher.addOrder(order);
        }
        assertEquals( startPx + (max_buys-1)*pxIncrement , matcher.getBuys().get(0).getPrice(), 0.00001);
        assertEquals( startPx , matcher.getBuys().get(max_buys-1).getPrice(), 0.00001);
    }

    @Test
    public void sellOrdering() {
        Matcher matcher = new Matcher( 100, 0.25);
        // Lowest BID is at the top
        int oid = 1;
        int max_sells = 5;
        double startPx = 123.45;
        double pxIncrement = 2.2;
        for(int i = 0; i < max_sells; i++) {
            ClientOrder order = OrderHelper.nextOrder(oid++);
            order.setIsBuy(false);
            order.setPrice(startPx + (i * pxIncrement));
            order.setQuantity(100);
            matcher.addOrder(order);
        }
        assertEquals( startPx , matcher.getSells().get(0).getPrice(), 0.00001);
        assertEquals( startPx + (max_sells-1)*pxIncrement , matcher.getSells().get(max_sells-1).getPrice(), 0.00001);
    }

    @Test
    public void minMaxTest() {
        Matcher matcher = new Matcher( 100, 0.25);
        assertEquals(Double.NaN, matcher.minPrice(), 0.00001);
        assertEquals(Double.NaN, matcher.maxPrice(), 0.00001);

        int oid = 1;
        ClientOrder one = OrderHelper.nextOrder(oid++);
        one.setIsBuy(true);
        one.setPrice(1.234);
        matcher.addOrder(one);
        assertEquals(1.234, matcher.minPrice(), 0.00001);

        ClientOrder two = OrderHelper.nextOrder(oid++);
        two.setIsBuy(true);
        two.setPrice(0.234);
        matcher.addOrder(two);
        assertEquals(0.234, matcher.minPrice(), 0.00001);

        ClientOrder three = OrderHelper.nextOrder(oid++);
        three.setIsBuy(false);
        three.setPrice(2.234);
        matcher.addOrder(three);
        assertEquals(2.234, matcher.maxPrice(), 0.00001);

        ClientOrder four = OrderHelper.nextOrder(oid++);
        four.setIsBuy(false);
        four.setPrice(3.234);
        matcher.addOrder(four);
        assertEquals(3.234, matcher.maxPrice(), 0.00001);
    }
}
