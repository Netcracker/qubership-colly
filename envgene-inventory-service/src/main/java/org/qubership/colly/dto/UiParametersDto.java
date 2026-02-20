package org.qubership.colly.dto;

import org.qubership.colly.db.data.ParamsetContext;

import java.util.List;
import java.util.Map;

public record UiParametersDto(Map<ParamsetContext, List<ParameterDto>> parameters) {
}
