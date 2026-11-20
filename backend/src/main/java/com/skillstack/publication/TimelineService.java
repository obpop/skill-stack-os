package com.skillstack.publication;

import com.skillstack.common.ApiException;
import com.skillstack.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class TimelineService {
    private final TimelineEventRepository events;
    private final UserService users;

    public TimelineService(TimelineEventRepository events, UserService users) {
        this.events = events;
        this.users = users;
    }

    @Transactional
    public TimelineEvent addManual(String subject, PublicationDtos.CreateTimelineRequest request) {
        if (request.type() != TimelineEventType.ACTION_COMPLETED && request.type() != TimelineEventType.PERSONAL_MILESTONE) {
            throw ApiException.badRequest("TIMELINE_TYPE_MANAGED", "Only completed actions and personal milestones can be added manually");
        }
        if (request.occurredOn().isAfter(LocalDate.now())) {
            throw ApiException.badRequest("FUTURE_MILESTONE", "A completed milestone cannot be dated in the future");
        }
        return events.save(new TimelineEvent(users.require(subject), request.type(), request.title(), request.description(),
                request.occurredOn(), TimelineDateSource.USER_ENTERED, "USER_MILESTONE", null));
    }

    @Transactional
    public void recordNextSkill(String subject, UUID recommendationId, String nextSkill) {
        if (events.existsByOwnerSubjectAndTypeAndSourceId(subject, TimelineEventType.NEXT_SKILL_SELECTED, recommendationId)) return;
        events.save(new TimelineEvent(users.require(subject), TimelineEventType.NEXT_SKILL_SELECTED,
                "Selected next skill: " + nextSkill, "This records a chosen development focus, not completed mastery.",
                null, TimelineDateSource.SERVER_RECORDED, "RECOMMENDATION", recommendationId));
    }

    @Transactional
    public void deleteManual(String subject, UUID id) {
        var event = events.findByIdAndOwnerSubject(id, subject).orElseThrow(() -> ApiException.notFound("timeline event"));
        if (!"USER_MILESTONE".equals(event.getSourceType())) {
            throw ApiException.badRequest("TIMELINE_EVENT_MANAGED", "Recorded product events cannot be deleted as personal milestones");
        }
        events.delete(event);
    }
}
