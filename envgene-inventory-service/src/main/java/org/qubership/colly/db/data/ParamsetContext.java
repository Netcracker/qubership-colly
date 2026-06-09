package org.qubership.colly.db.data;

public enum ParamsetContext {
    DEPLOYMENT("deployment"),
    RUNTIME("runtime"),
    PIPELINE("pipeline");

    private final String key;

    ParamsetContext(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static ParamsetContext fromKey(String key) {
        for (ParamsetContext ctx : values()) {
            if (ctx.key.equals(key)) return ctx;
        }
        return null;
    }
}
