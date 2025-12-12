package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudPassportData(CloudData cloud,
                                CSEData cse,
                                DBaaSData dbaas,
                                ArgocdData argocd) {
}
