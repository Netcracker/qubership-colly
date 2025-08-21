package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudPassportData {
    private CloudData cloud;
    private CSEData cse;


    public CloudData getCloud() {
        return cloud;
    }

    public void setCloud(CloudData cloud) {
        this.cloud = cloud;
    }

    public CSEData getCse() {
        return cse;
    }

    public void setCse(CSEData cse) {
        this.cse = cse;
    }
}
