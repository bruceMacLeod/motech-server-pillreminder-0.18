package org.motechproject.event.listener;

import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.MotechEventConfig;
import org.motechproject.event.listener.annotations.MotechListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath*:META-INF/motech/*.xml"})
public class ServerEventRelayIT {

    private static final String MESSAGE_REDELIVERY_TEST = "MESSAGE_REDELIVERY_TEST";

    @Autowired
    private ServerEventRelay eventRelay;

    @Autowired
    private EventListenerRegistry eventListenerRegistry;

    @Autowired
    private MotechEventConfig motechEventConfig;


    /**
     * For the test to work, set attribute schedulerSupport="true" in the broker element of the activemq.xml
     * Ref: http://activemq.apache.org/delay-and-schedule-message-delivery.html
     */
    @Test
    public void shouldRedeliverMessages_SpecifiedTimes_WithDelay() throws InterruptedException, NoSuchFieldException {
        InvalidMessageEventListener eventListener = new InvalidMessageEventListener();
        eventListenerRegistry.registerListener(eventListener, MESSAGE_REDELIVERY_TEST);

        MotechEvent testMessage = new MotechEvent(MESSAGE_REDELIVERY_TEST);
        eventRelay.sendEventMessage(testMessage);

        Boolean isDiscarded = null;
        MotechEvent motechEvent = null;
        for (int pollCount = 0; pollCount < 10; pollCount++) {
            motechEvent = eventListener.getMotechEvent();
            if (motechEvent != null) {
                isDiscarded = (Boolean) motechEvent.getParameters().get(MotechEvent.PARAM_DISCARDED_MOTECH_EVENT);
                if (isDiscarded != null && isDiscarded) {
                    break;
                }
            }
            Thread.sleep(3000);
        }
        assertTrue(isDiscarded != null && isDiscarded);
        assertEquals(motechEventConfig.getMessageMaxRedeliveryCount(), motechEvent.getMessageRedeliveryCount());
        assertEventHandledTimes(eventListener);
    }

    private void assertEventHandledTimes(InvalidMessageEventListener eventListener) {
        List<DateTime> handledTimes = eventListener.getHandledTimes();
        System.out.println("Handled at times: " + handledTimes);
        int messageMaxRedeliveryCount = motechEventConfig.getMessageMaxRedeliveryCount();
        long delay = motechEventConfig.getMessageRedeliveryDelay();

        for (int i = 0; i < messageMaxRedeliveryCount; i++) {
            long delta = delay * Double.valueOf(Math.pow(2, i)).intValue();
            long diff = handledTimes.get(i + 1).getMillis() - handledTimes.get(i).getMillis();
            diff = (long) Math.ceil(diff/1000.0);
            assertTrue(format("Expected retry after %d seconds, raised after %d seconds", delta, diff), diff >= delta);
        }
    }

    class InvalidMessageEventListener implements EventListener {

        private MotechEvent motechEvent;
        private List<DateTime> handledTimes = new ArrayList<>();

        @Override
        public String getIdentifier() {
            return MESSAGE_REDELIVERY_TEST;
        }

        @MotechListener(subjects = MESSAGE_REDELIVERY_TEST)
        public synchronized void handle(MotechEvent motechEvent) {
            this.motechEvent = motechEvent;
            handledTimes.add(new DateTime());
            throw new RuntimeException("Message redelivery test.");
        }

        public MotechEvent getMotechEvent() {
            return motechEvent;
        }

        public List<DateTime> getHandledTimes() {
            return handledTimes;
        }
    }


    @After
    public void teardown() {
        eventListenerRegistry.clearListenersForBean(MESSAGE_REDELIVERY_TEST);
    }
}
