package org.qubership.colly.data;

import java.util.Map;

public record ConfigMap(String name, Map<String, String> content, String configuration) {
}
