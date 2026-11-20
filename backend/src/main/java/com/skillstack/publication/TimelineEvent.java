package com.skillstack.publication;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "skill_timeline_event")
public class TimelineEvent extends AuditedEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 40) private TimelineEventType type;
    @Column(nullable = false, length = 180) private String title;
    @Column(length = 1000) private String description;
    @Column(name = "occurred_on") private LocalDate occurredOn;
    @Enumerated(EnumType.STRING) @Column(name = "date_source", nullable = false, length = 30) private TimelineDateSource dateSource;
    @Column(name = "source_type", length = 40) private String sourceType;
    @Column(name = "source_id") private UUID sourceId;

    protected TimelineEvent() {}

    public TimelineEvent(AppUser owner, TimelineEventType type, String title, String description,
                         LocalDate occurredOn, TimelineDateSource dateSource, String sourceType, UUID sourceId) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.type = type;
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.occurredOn = occurredOn;
        this.dateSource = dateSource;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public TimelineEventType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public TimelineDateSource getDateSource() { return dateSource; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
}
