package com.solacesystems.demo;

import com.solacesystems.conn.SolaceConnector;
import com.solacesystems.solclientj.core.event.FlowEventCallback;
import com.solacesystems.solclientj.core.event.MessageCallback;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JSONFnTest {
    static final MockMatchingEngineSerializer serializer = new MockMatchingEngineSerializer();
    static final ClientOrder input = OrderHelper.nextOrder(1);

    @Test
    public void appStateStructureTest() throws ParseException {
        String json = "{ \"Instance\":2, \"HAState\":\"ACTIVE\", \"SeqState\":\"UP_TO_DATE\", \"LastInput\":-1, \"LastOutput\":82, \"Data\": [\n" +
                "\t{ \"buy\": \"\", \"price\": 101.000, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"\", \"price\": 100.750, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"\", \"price\": 100.500, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"\", \"price\": 100.250, \"sell\": \"54321.0\" },\n" +
                "\t{ \"buy\": \"\", \"price\": 100.000, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"12345.0\", \"price\":  99.750, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"\", \"price\":  99.500, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"\", \"price\":  99.250, \"sell\": \"\" },\n" +
                "\t{ \"buy\": \"\", \"price\":  99.000, \"sell\": \"\" }\n" +
                "]\n" +
                " }";

        JSONParser parser = new JSONParser();
        JSONObject state = (JSONObject) parser.parse(json);
        Assert.assertEquals(2L, state.get("Instance"));
        Object o = state.get("Data");
        JSONArray ladder = (JSONArray)o;
                Assert.assertNotNull(ladder);
        JSONObject l4 = (JSONObject) ladder.get(3);
        Assert.assertEquals("54321.0", l4.get("sell"));
    }

    @Test
    public void clientOrderTest() throws Throwable {
        ByteBuffer buffer = serializer.SerializeInput(input);
        //MessageHandle mh = Solclient.Allocator.newMessageHandle();
        //Solclient.createMessageForHandle(mh);
        // flip() here would've been done had we sent+received the message via the SolaceConnector
        buffer.flip();
        //mh.setBinaryAttachment(buffer);
        ClientOrder result = serializer.DeserializeInput(buffer);

        assertEquals(input.getSequenceId(), result.getSequenceId());
        assertEquals(input.getInstrument(), result.getInstrument());
        assertEquals(input.getPrice(), result.getPrice(), 0.001);
        assertEquals(input.getQuantity(), result.getQuantity(), 0.001);
        assertEquals(input.isBuy(), result.isBuy());
    }


    @Test
    public void appStateTest() throws Throwable {
        MatcherState initState = new MatcherState();
        initState.setApp("appname");
        initState.setInstrument("AMZN");
        initState.setLastInput(1);
        initState.setMatcher(new Matcher(100, 0.25));
        ByteBuffer buffer = serializer.SerializeOutput(initState);
        //MessageHandle mh = Solclient.Allocator.newMessageHandle();
        //Solclient.createMessageForHandle(mh);
        // flip() here would've been done had we sent+received the message via the SolaceConnector
        buffer.flip();
        //mh.setBinaryAttachment(buffer);
        MatcherState result = serializer.DeserializeOutput(buffer);

        assertEquals(initState.getLastInput(), result.getLastInput());
        assertEquals(initState.getInstrument(), result.getInstrument());
    }

    @Test
    public void solaceSerializeEndToEnd() throws Exception {
        final AtomicInteger received = new AtomicInteger(0);

        SolaceConnector conn = new SolaceConnector();
        conn.ConnectSession("192.168.56.151", "ha_demo",
                "foo", "foo", "whatever",
                new SessionEventCallback() {
                    public void onEvent(SessionHandle sessionHandle) {}
                });

        final ByteBuffer retData = ByteBuffer.allocate(8192);
        conn.ProvisionQueue("fntests", 10 );
        conn.SubscribeQueueToTopic( "fntests", "delete/me" );
        FlowHandle flow = conn.BindQueue("fntests", new MessageCallback() {
                    public void onMessage(Handle handle) {
                        MessageSupport ms = (MessageSupport) handle;
                        MessageHandle msg = ms.getRxMessage();
                        int sz = msg.getBinaryAttachmentSize();
                        msg.getBinaryAttachment(retData);
                        retData.flip();
                        ClientOrder output = serializer.DeserializeInput(retData);
                        assertEquals(input.getSequenceId(), output.getSequenceId());
                        received.incrementAndGet();
                    }
                },
                new FlowEventCallback() {
                    public void onEvent(FlowHandle flowHandle) {}
                } );
        flow.start();


        ByteBuffer outbuff = serializer.SerializeInput(input);
        conn.SendOutput( "delete/me", outbuff );
        while(received.get() < 1) {
            try {
                Thread.sleep(200);
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        }

        flow.stop();
        flow.destroy();
        conn.DeprovisionQueue("fntests");
        conn.DisconnectSession();
        conn.destroy();
    }
}
