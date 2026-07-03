package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ParamsetContext {
    DEPLOYMENT("deployment"),
    RUNTIME("runtime"),
    PIPELINE("pipeline");

    private final String key;

    ParamsetContext(String key) {
        this.key = key;
    }

    @JsonValue
    public String key() {
        return key;
    }

    @JsonCreator
    public static ParamsetContext fromKey(String key) {
        for (ParamsetContext ctx : values()) {
            if (ctx.key.equals(key)) return ctx;
        }
        return null;
    }
}
