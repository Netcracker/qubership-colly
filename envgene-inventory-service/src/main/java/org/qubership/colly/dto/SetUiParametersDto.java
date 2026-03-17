package org.qubership.colly.dto;

import org.qubership.colly.db.data.ParamsetContext;

import java.util.Map;

public record SetUiParametersDto(CommitInfoDto commitInfo, Map<ParamsetContext, Map<String, Object>> parameters) {

    public SetUiParametersDto {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters must not be null");
        }
    }
}
