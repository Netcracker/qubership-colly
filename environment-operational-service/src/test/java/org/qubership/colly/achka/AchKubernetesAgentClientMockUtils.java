package org.qubership.colly.achka;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AchKubernetesAgentClientMockUtils {

    public static AchKubernetesAgentClient mockAchkaRestClient(AchKubernetesAgentClientFactory clientFactory, AchKubernetesAgentClient.AchkaResponse response) {
        AchKubernetesAgentClient client = mock(AchKubernetesAgentClient.class);
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.versions(anyList(), anyString())).thenReturn(response);
        return client;
    }
}

