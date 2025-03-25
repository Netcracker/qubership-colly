package org.qubership.colly.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudPassportData {
    private CloudData cloud;

    public CloudPassportData() {
    }

    public CloudData getCloud() {
        return cloud;
    }

    public void setCloud(CloudData cloud) {
        this.cloud = cloud;
    }
}
