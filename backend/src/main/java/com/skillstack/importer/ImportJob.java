package com.skillstack.importer;

import com.skillstack.common.AuditedEntity;
import com.skillstack.user.AppUser;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "import_job")
public class ImportJob extends AuditedEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private AppUser owner;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false, length = 20) private SourceType sourceType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(name = "proposed_items", nullable = false, columnDefinition = "jsonb") @ColumnTransformer(write = "CAST(? AS jsonb)") private String proposedItems;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(name = "accepted_at") private OffsetDateTime acceptedAt;

    public enum SourceType { TEXT, MARKDOWN, PDF, DOCX }
    public enum Status { PENDING, EXTRACTED, REVIEWED, FAILED }

    protected ImportJob() {}
    public ImportJob(AppUser owner, SourceType sourceType) {
        this.id = UUID.randomUUID(); this.owner = owner; this.sourceType = sourceType;
        this.status = Status.PENDING; this.proposedItems = "[]";
    }
    public void extracted(String proposedItems) { this.proposedItems = proposedItems; this.status = Status.EXTRACTED; this.errorCode = null; }
    public void reviewed() { this.status = Status.REVIEWED; this.acceptedAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public void failed(String errorCode) { this.status = Status.FAILED; this.errorCode = errorCode; this.proposedItems = "[]"; }
    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public SourceType getSourceType() { return sourceType; }
    public Status getStatus() { return status; }
    public String getProposedItems() { return proposedItems; }
    public String getErrorCode() { return errorCode; }
    public OffsetDateTime getAcceptedAt() { return acceptedAt; }
}
