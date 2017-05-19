package com.solacesystems.demo;

import com.solacesystems.model.HAState;
import com.solacesystems.model.SeqState;

class AppStateHelper {
    static public MatcherState makeAppState(int stackLevels, double par, double incr) {
        MatcherState state = new MatcherState();
        state.setApp( "jimmy1" );
        state.setInstance( 1 );
        state.setInstrument( "MSFT" );
        state.setHAStatus( HAState.CONNECTED );
        state.setSeqStatus( SeqState.RECOVERING );
        state.setLastInput( 12345L );
        state.setLastOutput( 123456L );

        int perSide = stackLevels / 2;
        Matcher matcher = new Matcher( par, incr );
        for( int i = 1; i <= perSide; i++ ) {
            matcher.addOrder( OrderHelper.makeBuy( i, "MSFT", par-(i*incr), 123.0 ) );
            matcher.addOrder( OrderHelper.makeSell( i+10, "MSFT", par+(i*incr), 321.0 ) );
        }
        state.setMatcher( matcher );
        return state;
    }
}
