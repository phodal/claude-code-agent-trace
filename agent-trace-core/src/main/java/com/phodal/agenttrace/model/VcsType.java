package com.phodal.agenttrace.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * Version control system types supported by Agent Trace.
 * 
 * @see <a href="https://agent-trace.dev/">Agent Trace Specification</a>
 */
public enum VcsType {
    GIT("git"),
    JJ("jj"),
    HG("hg"),
    SVN("svn"),
    /**
     * Unknown VCS type - used for forward compatibility when encountering
     * new VCS types not yet supported by this version.
     */
    UNKNOWN("unknown");

    private final String value;

    VcsType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Parse a VCS type from a string value.
     * Returns Optional.empty() for null/empty values.
     * Returns UNKNOWN for unrecognized values (forward compatibility).
     * 
     * @param value The string value to parse
     * @return Optional containing the VcsType, or empty for null/blank input
     */
    public static Optional<VcsType> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (VcsType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return Optional.of(type);
            }
        }
        // Forward compatibility: return UNKNOWN for unrecognized types
        return Optional.of(UNKNOWN);
    }

    /**
     * Parse a VCS type from a string value, defaulting to UNKNOWN.
     * Used by Jackson for deserialization.
     * 
     * @param value The string value to parse
     * @return The VcsType, defaulting to UNKNOWN for unrecognized values
     */
    @JsonCreator
    public static VcsType fromValueOrUnknown(String value) {
        return fromValue(value).orElse(UNKNOWN);
    }

    /**
     * Check if this is a known VCS type (not UNKNOWN).
     */
    public boolean isKnown() {
        return this != UNKNOWN;
    }
}
