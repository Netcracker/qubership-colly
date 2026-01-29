package org.qubership.colly.achka;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicationsVersion(
        @JsonProperty("source")
        String source,
        @JsonProperty("deploy_status")
        String deployStatus,
        @JsonProperty("deploy_date")
        String deployDate,
        @JsonProperty("ticket_id")
        String ticketId
) {
}
