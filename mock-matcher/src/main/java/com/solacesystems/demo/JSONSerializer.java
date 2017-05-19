package com.solacesystems.demo;

import com.solacesystems.model.HAState;
import com.solacesystems.model.SeqState;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

class JSONSerializer {
    private static final Logger logger = LoggerFactory.getLogger(JSONSerializer.class);

    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -
    ///
    ///         ClientOrder
    ///
    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -

    static JSONObject SerializeClientOrder(ClientOrder order) {
        JSONObject json = new JSONObject();
        json.put( "seqId" , new Long(order.getSequenceId()) );
        json.put( "instrument" , order.getInstrument() );
        json.put( "buyOrSell" , (order.isBuy() ? "B" : "S") );
        json.put( "quantity" , order.getQuantity() );
        json.put( "price" , order.getPrice() );
        json.put( "trader" , order.getTrader() );
        return json;

    }
    static ClientOrder DeserializeClientOrder(JSONObject json) {
        ClientOrder order = null;
        try {
            order = new ClientOrder( getLong(json, "seqId", 0L).intValue() );
            order.setInstrument( stringOrEmpty(json, "instrument") );
            order.setTrader( stringOrEmpty(json, "trader") );
            order.setIsBuy( ((String)json.get("buyOrSell")).charAt(0) == 'B' ? true : false );
            order.setQuantity( doubleOrNothing(json, "quantity") );
            order.setPrice( doubleOrNothing(json, "price") );
        }
        catch (Exception e) {
            logger.error("Error parsing incoming message");
            e.printStackTrace();
        }
        return order;
    }

    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -
    ///
    ///         AppState
    ///
    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -

    static JSONObject SerializeAppState(MatcherState output) {
        JSONObject json = new JSONObject();
        json.put( "app" , output.getApp() );
        json.put( "instance" , output.getInstance() );
        json.put( "instrument" , output.getInstrument() );
        json.put( "haStatus" , output.getHAStatus().toString() );
        json.put( "seqStatus" , output.getSeqStatus().toString() );
        json.put( "lastInput" , output.getLastInput() );
        json.put( "lastOutput" , output.getLastOutput() );
        json.put( "data", SerializeOrderStack(output.getMatcher()) );
        return json;
    }

    static MatcherState DeserializeAppState(JSONObject json) {
        MatcherState state = new MatcherState();
        try {
            state.setApp( stringOrEmpty(json, "app") );
            state.setInstance( getInt(json, "instance", -1) );
            state.setInstrument( stringOrEmpty(json, "instrument") );
            state.setHAStatus( HAState.valueOf(stringOrEmpty(json, "haStatus")) );
            state.setSeqStatus( SeqState.valueOf(stringOrEmpty(json, "seqStatus")) );
            state.setLastInput( getLong(json, "lastInput", -1L) );
            state.setLastOutput( getLong(json, "lastOutput", -1L) );
            state.setMatcher( DeserializeOrderStack( (JSONObject)json.get("data")) );
        }
        catch(Exception e) {
            logger.error("Error parsing incoming JSON message");
            e.printStackTrace();
        }
        return state;
    }


    static JSONObject SerializeOrderStack(Matcher matcher) {
        JSONObject jsMatcher = new JSONObject();
        jsMatcher.put( "par", matcher.getPar() );
        jsMatcher.put( "priceInc", matcher.getPriceIncrement() );
        // buys
        JSONArray buys = new JSONArray();
        for(Matcher.Lvl buy : matcher.getBuys()) {
            buys.add( SerializeStackLevel(buy) );
        }
        jsMatcher.put( "buys", buys );
        // sells
        JSONArray  sells = new JSONArray();
        for(Matcher.Lvl sell : matcher.getSells()) {
            sells.add( SerializeStackLevel(sell) );
        }
        jsMatcher.put( "sells", sells );

        return jsMatcher;
    }
    static Matcher DeserializeOrderStack(JSONObject json) {
        Double par   = (Double) json.get( "par" );
        Double delta = (Double) json.get( "priceInc" );
        Matcher matcher = new Matcher( par, delta );

        DeserializeStackSide( (JSONArray)json.get("buys"), matcher.getBuys() );
        DeserializeStackSide( (JSONArray)json.get("sells"), matcher.getSells() );

        return matcher;
    }
    static void DeserializeStackSide(JSONArray source, List<Matcher.Lvl> target) {
        for( Object o : source) {
            JSONObject jsBuy = (JSONObject) o;
            target.add( DeserializeStackLevel(jsBuy) );
        }
    }

    static JSONObject SerializeStackLevel(Matcher.Lvl lvl) {
        JSONObject o = new JSONObject();
        o.put( "price" , lvl.getPrice() );
        o.put( "quantity" , lvl.getQuantity() );
        return o;
    }
    static Matcher.Lvl DeserializeStackLevel(JSONObject json) {
        return new Matcher.Lvl(
                (Double)json.get("price"), (Double)json.get("quantity")
        );
    }

    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -
    ///
    ///         Trade
    ///
    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -
    static JSONObject SerializeTrade(Trade trade) {
        JSONObject json = new JSONObject();
        json.put( "instrument" , trade.getInstrument() );
        json.put( "price" , trade.getPrice() );
        json.put( "quantity" , trade.getQuantity() );
        return json;
    }

    static Trade DeserializeTrade(JSONObject json) {
        Trade trade = new Trade();
        trade.setInstrument( stringOrEmpty(json, "instrument") );
        trade.setQuantity( doubleOrNothing(json, "quantity") );
        trade.setPrice( doubleOrNothing(json, "price") );
        return trade;
    }


    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -
    ///
    ///         HELPER FUNCTIONS
    ///
    /// - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + - + -

    static String stringOrEmpty(JSONObject json, String key) {
        if (json.containsKey(key)) {
            Object v = json.get(key);
            if (v instanceof String)
                return (String) v;
            if (v != null)
                return v.toString();
        }
        return "";
    }

    static Double doubleOrNothing(JSONObject json, String key) {
        if (json.containsKey(key)) {
            Object v = json.get(key);
            if (v instanceof Double)
                return (Double) v;
        }
        return Double.NaN;
    }

    static Long getLong(JSONObject json, String key, Long defaultValue) {
        if (json.containsKey(key)) {
            Object v = json.get(key);
            if (v instanceof Long)
                return (Long) v;
        }
        return defaultValue;
    }

    static Integer getInt(JSONObject json, String key, Integer defaultValue) {
        if (json.containsKey(key)) {
            Object v = json.get(key);
            if (v instanceof Integer)
                return (Integer) v;
        }
        return defaultValue;
    }

    final static private JSONParser _parser = new JSONParser();
    //final static private DecimalFormat _df = new DecimalFormat("#.####");
}
