package org.motechproject.decisiontree.core.model;

import java.util.HashMap;
import java.util.Map;

import org.motechproject.decisiontree.core.FlowSession;
import org.motechproject.event.MotechEvent;
import org.motechproject.event.listener.EventRelay;
import org.springframework.beans.factory.annotation.Autowired;

public class EventTransition extends Transition {

    @Autowired
    private EventRelay eventRelay;

    private String eventSubject;

    public void setEventSubject(String eventSubject) {
        this.eventSubject = eventSubject;
    }

    public String getEventSubject() {
        return eventSubject;
    }

    @Override
    public Node getDestinationNode(String input, FlowSession session) {
        raiseEventWithData(session.getSessionId());
        return super.getDestinationNode(input, session);
    }

    private void raiseEventWithData(String sessionId) {
        Map<String, Object> params = new HashMap<>();
        params.put("flowSessionId", sessionId);
        MotechEvent motechEvent = new MotechEvent(eventSubject, params);
        eventRelay.sendEventMessage(motechEvent);
    }

}