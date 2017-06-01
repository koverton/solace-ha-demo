package com.solacesystems.demo;

import com.solacesystems.ha.model.HAState;
import com.solacesystems.ha.model.SeqState;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.Test;

import java.util.List;

import static com.solacesystems.demo.JSONSerializer.stringOrEmpty;
import static org.junit.Assert.assertEquals;

public class JSONTest {

    @Test
    public void clientOrderRoundtripTest() {
        // Test Serialization
        ClientOrder order = OrderHelper.nextOrder( 1 );
        JSONObject  json  = JSONSerializer.SerializeClientOrder( order );
        System.out.println( "Testing clientOrder: " + json.toJSONString() );
        // Compare scalars
        assertEquals( order.getSequenceId(), ((Long)json.get("seqId")).intValue() );
        assertEquals( order.getInstrument(), json.get("instrument") );
        assertEquals( order.getQuantity()  , (Double)json.get("quantity"), 0.001 );
        assertEquals( order.getPrice()     , (Double)json.get("price"), 0.001 );
        assertEquals( json.get("buyOrSell"), (order.isBuy() ? "B" : "S") );

        // Test Deserialization
        ClientOrder last = JSONSerializer.DeserializeClientOrder( json );
        assertEquals( order.getSequenceId(), last.getSequenceId() );
        assertEquals( order.getInstrument(), last.getInstrument() );
        assertEquals( order.getQuantity()  , last.getQuantity(), .001 );
        assertEquals( order.getPrice()     , last.getPrice(), .001 );
        assertEquals( order.isBuy()        , last.isBuy() );
    }

    @Test
    public void appStateRoundtripTest() {
        // Test Serialization
        MatcherState state = AppStateHelper.makeAppState( 8, 100, 0.25 );
        JSONObject json= JSONSerializer.SerializeMatcherState( state );
        System.out.println( "Testing appstate: " + json.toJSONString() );
        // Compare scalars
        assertEquals( state.getApp(), json.get("app") );
        assertEquals( state.getInstance() , json.get("instance") );
        assertEquals( state.getInstrument(), json.get("instrument") );
        assertEquals( state.getHAStatus() , HAState.valueOf(stringOrEmpty(json, "haStatus")) );
        assertEquals( state.getSeqStatus(), SeqState.valueOf(stringOrEmpty(json, "seqStatus")) );
        assertEquals( state.getLastInput(), json.get("lastInput") );
        assertEquals( state.getLastOutput(), json.get("lastOutput") );
        // Compare the matcher OrderStack
        testMatcher( state.getMatcher(), (JSONObject)json.get("data") );

        // Test Deserialization
        MatcherState last = JSONSerializer.DeserializeMatcherState( json );
        // Compare scalars
        assertEquals( state.getApp(),       last.getApp() );
        assertEquals( state.getInstance() , last.getInstance() );
        assertEquals( state.getInstrument(),last.getInstrument() );
        assertEquals( state.getHAStatus() , last.getHAStatus() );
        assertEquals( state.getSeqStatus(), last.getSeqStatus() );
        assertEquals( state.getLastInput(), last.getLastInput() );
        assertEquals( state.getLastOutput(),last.getLastOutput() );
        testMatcher( last.getMatcher(), (JSONObject)json.get("data") );
    }

    private void testMatcher(Matcher matcher, JSONObject jsMatcher) {
        assertEquals( matcher.getPriceIncrement(), (Double)jsMatcher.get("priceInc"), .0001 );
        // test buys array
        JSONArray jsBuys = (JSONArray) jsMatcher.get("buys");
        List<Matcher.Lvl> buys = matcher.getBuys();
        assertEquals( buys.size(), jsBuys.size() );
        for( int i = 0; i < buys.size(); i++ ) {
            Matcher.Lvl lvl = buys.get(i);
            JSONObject jsLvl = (JSONObject) jsBuys.get(i);
            assertEquals( "Bad buy price at level " + i,
                    lvl.getPrice(), (Double)jsLvl.get("price"), .0001 );
            assertEquals( "Bad buy quantity at level " + i,
                    lvl.getQuantity(), (Double)jsLvl.get("quantity"), .0001 );
        }
        // test sells array
        JSONArray jsSells = (JSONArray) jsMatcher.get("sells");
        List<Matcher.Lvl> sells = matcher.getSells();
        assertEquals( sells.size(), jsSells.size() );
        for( int i = 0; i < sells.size(); i++ ) {
            Matcher.Lvl lvl = sells.get(i);
            JSONObject jsLvl = (JSONObject) jsSells.get(i);
            assertEquals( "Bad sell price at level " + i,
                    lvl.getPrice(), (Double)jsLvl.get("price"), .0001 );
            assertEquals( "Bad sell quantity at level " + i,
                    lvl.getQuantity(), (Double)jsLvl.get("quantity"), .0001 );
        }
    }

}
