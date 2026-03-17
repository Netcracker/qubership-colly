package org.qubership.colly.dto;

import org.qubership.colly.db.data.ParamsetContext;

import java.util.Map;

public record UiParametersDto(Map<ParamsetContext, Map<String, Object>> parameters) {
}
