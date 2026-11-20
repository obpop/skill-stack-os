package com.skillstack.stack;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "development_item")
public class DevelopmentItem {
    @Id @Column(length = 100) private String id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Category category;
    @Enumerated(EnumType.STRING) @Column(length = 30) private Subcategory subcategory;
    @Column(nullable = false, length = 240) private String title;
    @Column(length = 1000) private String url;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Cost cost;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Format format;
    @Column(length = 120) private String city;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Lever lever;
    @Column(name = "related_skills", nullable = false, columnDefinition = "jsonb") @ColumnTransformer(write = "CAST(? AS jsonb)") private String relatedSkills;
    @Column(name = "related_interests", nullable = false, columnDefinition = "jsonb") @ColumnTransformer(write = "CAST(? AS jsonb)") private String relatedInterests;
    @Column(nullable = false, columnDefinition = "jsonb") @ColumnTransformer(write = "CAST(? AS jsonb)") private String roles;
    @Column(name = "min_timeline_years", nullable = false) private short minTimelineYears;
    @Column(name = "max_timeline_years", nullable = false) private short maxTimelineYears;

    public enum Category { EDUCATION, NETWORK, JOB_TITLE, BUILD }
    public enum Subcategory { FORMAL, INFORMAL, BOOK }
    public enum Cost { FREE, PAID }
    public enum Format { ONLINE, IN_PERSON }
    public enum Lever { INCOME, ATTRACTIVENESS, LEVERAGE }

    protected DevelopmentItem() {}
    public String getId() { return id; }
    public Category getCategory() { return category; }
    public Subcategory getSubcategory() { return subcategory; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public Cost getCost() { return cost; }
    public Format getFormat() { return format; }
    public String getCity() { return city; }
    public Lever getLever() { return lever; }
    public String getRelatedSkills() { return relatedSkills; }
    public String getRelatedInterests() { return relatedInterests; }
    public String getRoles() { return roles; }
    public short getMinTimelineYears() { return minTimelineYears; }
    public short getMaxTimelineYears() { return maxTimelineYears; }
}
