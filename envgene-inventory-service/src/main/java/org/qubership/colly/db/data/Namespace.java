package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.ALWAYS)
@NoArgsConstructor
public class Namespace {
    private String uid;
    private String name;
    private String deployPostfix;

}
