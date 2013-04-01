package org.motechproject.server.verboice.web;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.motechproject.decisiontree.core.FlowSession;
import org.motechproject.decisiontree.server.domain.CallDetailRecord;
import org.motechproject.decisiontree.server.domain.CallDetailRecord.Disposition;
import org.motechproject.decisiontree.server.domain.CallDirection;
import org.motechproject.decisiontree.server.domain.CallEvent;
import org.motechproject.decisiontree.server.domain.FlowSessionRecord;
import org.motechproject.decisiontree.server.service.CalllogSearchService;
import org.motechproject.decisiontree.server.service.DecisionTreeServer;
import org.motechproject.decisiontree.server.service.FlowSessionService;
import org.motechproject.ivr.service.SessionNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

@Controller
public class VerboiceIVRController {

    @Autowired
    private FlowSessionService flowSessionService;
    @Autowired
    private DecisionTreeServer decisionTreeServer;
    @Autowired
    private CalllogSearchService searchService;

    public VerboiceIVRController() {
    }

    @RequestMapping("/ivr")
    public ModelAndView handle(HttpServletRequest request, HttpServletResponse response) {
        String verboiceCallId = request.getParameter("CallSid");
        String motechCallId = request.getParameter("motech_call_id");
        String phoneNumber = request.getParameter("From");
        FlowSession session = null;
        if (motechCallId == null) {
            session = flowSessionService.findOrCreate(verboiceCallId, phoneNumber);
        } else {
            session = updateOutgoingCallSessionIdWithVerboiceSid(motechCallId, verboiceCallId);
        }

        String tree = request.getParameter("tree");
        String language = request.getParameter("ln");
        String digits = request.getParameter("DialCallStatus");
        if (StringUtils.isBlank(digits)) {
            digits = request.getParameter("Digits");
        }

        session.setLanguage(language);
        session = setCustomParams(session, request);
        flowSessionService.updateSession(session);

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");

        ModelAndView view = decisionTreeServer.getResponse(verboiceCallId, phoneNumber, "verboice", tree, digits, language);
        view.addObject("contextPath", request.getContextPath());
        view.addObject("servletPath", request.getServletPath());
        view.addObject("host", request.getHeader("Host"));
        view.addObject("scheme", request.getScheme());
        return view;
    }

    @RequestMapping(value = "/ivr/callstatus", method = RequestMethod.GET)
    public void handleMissedCall(HttpServletRequest request) {
        String callStatus = request.getParameter("CallStatus");
        String callSid = request.getParameter("CallSid");
        String motechCallId = request.getParameter("motech_call_id");
        List<String> missedCallStatuses = Arrays.asList("busy", "failed", "no-answer");

        if (callStatus == null || callStatus.trim().isEmpty() || !missedCallStatuses.contains(callStatus)) {
            if ("ringing".equals(callStatus)) {
                CallDetailRecord record = ((FlowSessionRecord)flowSessionService.getSession(motechCallId)).getCallDetailRecord();
                record.setDisposition(Disposition.UNKNOWN);
                record.setCallDirection(CallDirection.Outbound);
                record.setCallId(callSid);
                CallEvent callEvent = new CallEvent("ringing");
                record.addCallEvent(callEvent);
                searchService.saveCallDetail(record);
            } else if ("in-progress".equals(callStatus)) {
                CallDetailRecord record = searchService.getCallDetailById(callSid);
                record.setDisposition(Disposition.ANSWERED);
                CallEvent callEvent = new CallEvent("in-progress");
                record.addCallEvent(callEvent);
                searchService.saveCallDetail(record);
            } else if ("completed".equals(callStatus)) {
                CallDetailRecord record = searchService.getCallDetailById(callSid);
                record.setDisposition(Disposition.ANSWERED);
                record.setEndDate(DateTime.now());
                CallEvent callEvent = new CallEvent("completed");
                record.addCallEvent(callEvent);
                searchService.saveCallDetail(record);
            }
            return;
        }
        
        updateMissedCall(callStatus, callSid);

        FlowSession session = flowSessionService.getSession(motechCallId);
        if (session == null) {
            throw new SessionNotFoundException("No session found! [Session Id " + motechCallId + "]");
        }

        session = flowSessionService.updateSessionId(motechCallId, callSid);
        decisionTreeServer.handleMissedCall(session.getSessionId());
    }

    private void updateMissedCall(String callStatus, String callSid) {
        if ("busy".equals(callStatus)) {
            CallDetailRecord record = searchService.getCallDetailById(callSid);
            record.setDisposition(Disposition.BUSY);
            record.setCallDirection(CallDirection.Outbound);
            record.setEndDate(DateTime.now());
            CallEvent callEvent = new CallEvent("busy");
            record.addCallEvent(callEvent);
            searchService.saveCallDetail(record);
        } else if ("failed".equals(callStatus)) {
            CallDetailRecord record = searchService.getCallDetailById(callSid);
            record.setDisposition(Disposition.FAILED);
            record.setEndDate(DateTime.now());
            CallEvent callEvent = new CallEvent("failed");
            record.addCallEvent(callEvent);
            searchService.saveCallDetail(record);
        } else if ("no-answer".equals(callStatus)) {
            CallDetailRecord record = searchService.getCallDetailById(callSid);
            record.setDisposition(Disposition.NO_ANSWER);
            record.setEndDate(DateTime.now());
            CallEvent callEvent = new CallEvent("no answer");
            record.addCallEvent(callEvent);
            searchService.saveCallDetail(record);
        }
    }

    private FlowSession updateOutgoingCallSessionIdWithVerboiceSid(String callId, String verboiceCallId) {
        FlowSession flowSession = flowSessionService.getSession(callId);
        return flowSessionService.updateSessionId(flowSession.getSessionId(), verboiceCallId);
    }

    private FlowSession setCustomParams(FlowSession session, HttpServletRequest request) {
        Map params = request.getParameterMap();
        Set<String> keys = params.keySet();
        for (String key : keys) {
            if (!asList("CallSid", "AccountSid", "From", "To", "CallStatus", "ApiVersion", "Direction", "ForwardedFrom", "CallerName", "FromCity", "FromState", "FromZip", "FromCountry", "ToCity", "ToState", "ToZip", "ToCountry", "ln").contains(key)) {
                session.set(key, (Serializable) params.get(key));
            }
        }
        return session;
    }
}
