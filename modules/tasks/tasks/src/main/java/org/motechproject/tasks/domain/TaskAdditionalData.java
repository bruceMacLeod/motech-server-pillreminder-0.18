package org.motechproject.tasks.domain;

import java.util.Objects;

public class TaskAdditionalData {
    private Long id;
    private String type;
    private String lookupField;
    private String lookupValue;

    public TaskAdditionalData() {
        this(null, null, null, null);
    }

    public TaskAdditionalData(Long id, String type, String lookupField, String lookupValue) {
        this.id = id;
        this.type = type;
        this.lookupField = lookupField;
        this.lookupValue = lookupValue;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLookupField() {
        return lookupField;
    }

    public void setLookupField(String lookupField) {
        this.lookupField = lookupField;
    }

    public String getLookupValue() {
        return lookupValue;
    }

    public void setLookupValue(String lookupValue) {
        this.lookupValue = lookupValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, lookupField, lookupValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final TaskAdditionalData other = (TaskAdditionalData) obj;

        return Objects.equals(this.id, other.id) && Objects.equals(this.type, other.type) &&
                Objects.equals(this.lookupField, other.lookupField) &&
                Objects.equals(this.lookupValue, other.lookupValue);
    }

    @Override
    public String toString() {
        return String.format("TaskAdditionalData{id=%d, type='%s', lookupField='%s', lookupValue='%s'}",
                id, type, lookupField, lookupValue);
    }
}
