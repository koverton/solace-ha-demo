package com.solacesystems.ha;

import com.solacesystems.ha.conn.SolaceConnector;
import com.solacesystems.ha.model.*;
import com.solacesystems.solclientj.core.event.SessionEventCallback;
import com.solacesystems.solclientj.core.handle.SessionHandle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class ClusterFunctionalTest {

    private static TestHAApplication createAndStartInstance(int instance, String inputQueue, String inputTopic, String outputTopic, String stateTopic) {
        final String stateQueue = "fntest_state_" + instance;
        TestHAApplication app = new TestHAApplication(instance, outputTopic);
        // Each instance listens to the other's output via it's State Queue
        app.Start(inputQueue, inputTopic, stateQueue, stateTopic);
        return app;
    }

    private final String inputTopic    = "fntest/input";
    private final String inst1OutTopic = "fntest/out/1";
    private final String inst2OutTopic = "fntest/out/2";
    private final String inputQueue    = "fntest_input";

    private TestHAApplication instance1, instance2;
    private ClusterModel<Integer,Double> model1, model2;
    private SolaceConnector inputSource;

    @Before
    public void setup() {
        // Each instance listens to the other's output via it's State Queue
        instance1 = createAndStartInstance( 1, inputQueue, inputTopic, inst1OutTopic, inst2OutTopic );
        waitabit();
        instance2 = createAndStartInstance( 2, inputQueue, inputTopic, inst2OutTopic, inst1OutTopic );
        waitabit();
        model1 = instance1.getModel();
        model2 = instance2.getModel();

        // Create a connection to send in test input
        inputSource = new SolaceConnector();
        inputSource.ConnectSession( ConnectionFields.HOST, ConnectionFields.VPN, ConnectionFields.USER, ConnectionFields.PASS, "Input",
                new SessionEventCallback() {
                    public void onEvent(SessionHandle sessionHandle) {}
                });
    }

    @Test
    public void theTest() {
        // Verify first bound should become Active, other BACKUP
        assertEquals( HAState.ACTIVE, model1.GetHAStatus() );
        assertEquals( SeqState.UP_TO_DATE, model1.GetSequenceStatus() );
        assertEquals( HAState.BACKUP, model2.GetHAStatus() );
        assertEquals( SeqState.FOLLOWING, model2.GetSequenceStatus() );

        TestSerializer inputSerializer = new TestSerializer();


        //Send some input...
        Integer input = 0;
        inputSource.SendBuffer( inputTopic, inputSerializer.SerializeInput(++input) );
        inputSource.SendBuffer( inputTopic, inputSerializer.SerializeInput(++input) );
        waitabit();

        // Verify the backup follows the active state
        assertEquals( SeqState.FOLLOWING, model2.GetSequenceStatus() );
        assertEquals( input, model1.GetLastInput() );
        assertEquals( model1.GetLastOutput(), model2.GetLastOutput() );

        // Kill the first instance and verify the second becomes ACTIVE
        instance1.Destroy();
        waitabit();
        assertEquals( HAState.ACTIVE, model2.GetHAStatus() );

        // Send some more input...
        inputSource.SendBuffer( inputTopic, inputSerializer.SerializeInput(++input) );
        inputSource.SendBuffer( inputTopic, inputSerializer.SerializeInput(++input) );
        waitabit();
        // Verify the second is now active and up to date
        assertEquals( input, model2.GetLastInput() );
        assertEquals( SeqState.UP_TO_DATE, model2.GetSequenceStatus() );

        // Reconnect inst1 which now becomes backup
        instance1 = createAndStartInstance( 1, inputQueue, inputTopic, inst1OutTopic, inst2OutTopic );
        waitabit();
        model1 = instance1.getModel();
        // Verify inst1 becomes backup and follows the state from the primary
        assertEquals( HAState.BACKUP, model1.GetHAStatus() );
        assertEquals( SeqState.FOLLOWING, model1.GetSequenceStatus() );
        assertEquals( model2.GetLastOutput(), model1.GetLastOutput() );
    }

    @After
    public void tearDown() {
        instance1.Destroy();
        instance2.Destroy();

        inputSource.DeprovisionQueue( inputQueue );
        inputSource.DeprovisionQueue( instance1.getStateQueueName() );
        inputSource.DeprovisionQueue( instance2.getStateQueueName() );

        inputSource.DisconnectSession();
        inputSource.destroy();
    }



    private boolean waitabit() {
        boolean interrupted = false;
        try {
            Thread.sleep(200);
        }
        catch(InterruptedException ex) {
            ex.printStackTrace();
            interrupted = true;
        }
        return interrupted;
    }
}
