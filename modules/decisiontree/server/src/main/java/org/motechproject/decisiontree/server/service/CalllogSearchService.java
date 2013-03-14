package org.motechproject.decisiontree.server.service;

import org.motechproject.decisiontree.core.CallDetail;
import org.motechproject.decisiontree.server.domain.CallDetailRecord;

import java.util.List;

public interface CalllogSearchService {

    List<CallDetail> search(CalllogSearchParameters searchParameters);

    long count(CalllogSearchParameters params);

    long findMaxCallDuration();

    List<String> getAllPhoneNumbers();

    void saveCallDetail(CallDetailRecord callDetail);

    CallDetailRecord getCallDetailById(String id);
}
