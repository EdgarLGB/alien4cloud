package org.alien4cloud.alm.deployment.configuration.model;

import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.elasticsearch.annotation.ESObject;
import org.elasticsearch.annotation.ObjectField;

@Getter
@Setter
@ESObject
@NoArgsConstructor
public class OrchestratorDeploymentProperties extends AbstractDeploymentConfig {
    /** The orchestrator id associated with the properties. */
    private String orchestratorId;
    /** Configuration of the deployment properties specific to the orchestrator if any. */
    @ObjectField(enabled = false)
    private Map<String, String> providerDeploymentProperties;

    public OrchestratorDeploymentProperties(String versionId, String environmentId) {
        super(versionId, environmentId);
    }
}