package org.qubership.colly.monitoring;

import java.util.List;
import java.util.Map;

public class MonitoringResponse {

    public String status;
    public Data data;

    public static class Data {
        public String resultType;
        public List<Result> result;
    }

    public static class Result {
        public Map<String, String> metric;
        public List<String> value;  // [timestamp, value]
    }
}
