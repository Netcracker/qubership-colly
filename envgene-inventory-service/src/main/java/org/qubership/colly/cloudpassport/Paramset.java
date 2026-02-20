package org.qubership.colly.cloudpassport;

import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.db.data.ParamsetLevel;

import java.util.Map;

public record Paramset(
        ParamsetContext paramsetContext,
        ParamsetLevel level,
        String deployPostfix,
        String applicationName,
        Map<String, String> parameters
) {
}
