package org.motechproject.scheduletracking.api.service.impl;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.motechproject.model.Time;
import org.motechproject.scheduler.MotechSchedulerService;
import org.motechproject.scheduletracking.api.domain.*;
import org.motechproject.scheduletracking.api.domain.exception.InvalidEnrollmentException;
import org.motechproject.scheduletracking.api.repository.AllEnrollments;
import org.motechproject.scheduletracking.api.repository.AllTrackedSchedules;
import org.motechproject.scheduletracking.api.service.EnrollmentRequest;
import org.motechproject.scheduletracking.api.service.ScheduleTrackingService;
import org.motechproject.valueobjects.WallTime;
import org.motechproject.valueobjects.WallTimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.motechproject.scheduletracking.api.utility.DateTimeUtil.wallTimeOf;
import static org.motechproject.scheduletracking.api.utility.DateTimeUtil.weeksAgo;
import static org.motechproject.util.DateUtil.today;

public class ScheduleTrackingServiceImplTest {
    @Mock
    private AllTrackedSchedules allTrackedSchedules;
    @Mock
    private MotechSchedulerService schedulerService;
    @Mock
    private AllEnrollments allEnrollments;
    @Mock
    private EnrollmentService enrollmentService;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void shouldEnrollEntityIntoFirstMilestoneOfSchedule() {
        String scheduleName = "my_schedule";
        Schedule schedule = new Schedule(scheduleName);
        Milestone secondMilestone = new Milestone("second_milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        Milestone firstMilestone = new Milestone("first_milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);

        ScheduleTrackingService scheduleTrackingService = new ScheduleTrackingServiceImpl(allTrackedSchedules, allEnrollments, enrollmentService);

        String externalId = "my_entity_1";
        LocalDate referenceDate = new LocalDate(2012, 1, 2);
        Time preferredAlertTime = new Time(8, 10);
        scheduleTrackingService.enroll(new EnrollmentRequest(externalId, scheduleName, preferredAlertTime, referenceDate));

        verify(enrollmentService).enroll(externalId, scheduleName, firstMilestone.getName(), referenceDate, today(), preferredAlertTime);
    }

    @Test
    public void shouldEnrollEntityIntoGivenMilestoneOfTheSchedule() {
        Milestone secondMilestone = new Milestone("second_milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        Milestone firstMilestone = new Milestone("first_milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        String scheduleName = "my_schedule";
        Schedule schedule = new Schedule(scheduleName);
        schedule.addMilestones(firstMilestone, secondMilestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);

        ScheduleTrackingService scheduleTrackingService = new ScheduleTrackingServiceImpl(allTrackedSchedules, allEnrollments, enrollmentService);

        String externalId = "entity_1";
        Time preferredAlertTime = new Time(8, 10);
        LocalDate referenceDate = new LocalDate(2012, 11, 2);
        scheduleTrackingService.enroll(new EnrollmentRequest(externalId, scheduleName, preferredAlertTime, referenceDate, secondMilestone.getName()));

        verify(enrollmentService).enroll(externalId, scheduleName, secondMilestone.getName(), referenceDate, today(), preferredAlertTime);
    }

    @Test
    public void shouldScheduleOneRepeatJobForTheSingleAlertInTheFirstMilestone() {
        Milestone milestone = new Milestone("milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        milestone.addAlert(WindowName.earliest, new Alert(new WallTime(0, null), new WallTime(1, WallTimeUnit.Day), 3, 0));
        String scheduleName = "my_schedule";
        Schedule schedule = new Schedule(scheduleName);
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName(scheduleName)).thenReturn(schedule);

        ScheduleTrackingService scheduleTrackingService = new ScheduleTrackingServiceImpl(allTrackedSchedules, allEnrollments, enrollmentService);
        String externalId = "entity_1";
        LocalDate referenceDate = new LocalDate(2012, 11, 2);
        Time preferredAlertTime = new Time(8, 10);
        scheduleTrackingService.enroll(new EnrollmentRequest(externalId, scheduleName, preferredAlertTime, referenceDate));

        verify(enrollmentService).enroll(externalId, scheduleName, milestone.getName(), referenceDate, today(), preferredAlertTime);
    }

    @Test
    public void shouldFulfillTheCurrentMilestone() {
        Milestone milestone = new Milestone("milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        milestone.addAlert(WindowName.earliest, new Alert(new WallTime(0, null), new WallTime(1, WallTimeUnit.Day), 3, 0));
        Schedule schedule = new Schedule("my_schedule");
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName("my_schedule")).thenReturn(schedule);

        ScheduleTrackingService scheduleTrackingService = new ScheduleTrackingServiceImpl(allTrackedSchedules, allEnrollments, enrollmentService);

        when(allEnrollments.getActiveEnrollment("entity_1", "my_schedule")).thenReturn(null);
        scheduleTrackingService.enroll(new EnrollmentRequest("entity_1", "my_schedule", new Time(8, 10), new LocalDate(2012, 11, 2)));

        Enrollment enrollment = mock(Enrollment.class);
        when(allEnrollments.getActiveEnrollment("entity_1", "my_schedule")).thenReturn(enrollment);

        scheduleTrackingService.fulfillCurrentMilestone("entity_1", "my_schedule");

        verify(enrollmentService).fulfillCurrentMilestone(enrollment);
    }

    @Test
    public void shouldUnenrollEntityFromTheSchedule() {
        Milestone milestone = new Milestone("milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        milestone.addAlert(WindowName.earliest, new Alert(new WallTime(0, null), new WallTime(1, WallTimeUnit.Day), 3, 0));
        Schedule schedule = new Schedule("my_schedule");
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName("my_schedule")).thenReturn(schedule);

        ScheduleTrackingService scheduleTrackingService = new ScheduleTrackingServiceImpl(allTrackedSchedules, allEnrollments, enrollmentService);

        Enrollment enrollment = new Enrollment("entity_1", "my_schedule", "milestone", weeksAgo(4), weeksAgo(4), new Time(8, 10));
        when(allEnrollments.getActiveEnrollment("entity_1", "my_schedule")).thenReturn(enrollment);
        scheduleTrackingService.unenroll("entity_1", "my_schedule");

        verify(enrollmentService).unenroll(enrollment);
    }

    @Test(expected = InvalidEnrollmentException.class)
    public void shouldThrowExceptionIfEntityIsNotEnrolledIntoSchedule() {
        Milestone milestone = new Milestone("milestone", wallTimeOf(1), wallTimeOf(2), wallTimeOf(3), wallTimeOf(4));
        milestone.addAlert(WindowName.earliest, new Alert(new WallTime(0, null), new WallTime(1, WallTimeUnit.Day), 3, 0));
        Schedule schedule = new Schedule("my_schedule");
        schedule.addMilestones(milestone);
        when(allTrackedSchedules.getByName("my_schedule")).thenReturn(schedule);

        ScheduleTrackingService scheduleTrackingService = new ScheduleTrackingServiceImpl(allTrackedSchedules, allEnrollments, enrollmentService);

        when(allEnrollments.getActiveEnrollment("entity_1", "my_schedule")).thenReturn(null);
        scheduleTrackingService.unenroll("entity_1", "my_schedule");
    }
}