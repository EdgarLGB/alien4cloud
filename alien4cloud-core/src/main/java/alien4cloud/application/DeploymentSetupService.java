package alien4cloud.application;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.elasticsearch.index.query.QueryBuilders;
import org.springframework.stereotype.Service;

import alien4cloud.cloud.CloudResourceMatcherService;
import alien4cloud.cloud.CloudService;
import alien4cloud.component.model.IndexedNodeType;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.Cloud;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.topology.TopologyServiceCore;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.model.PropertyDefinition;

import com.google.common.collect.Maps;

/**
 * Manages deployment setups.
 */
@Service
public class DeploymentSetupService {

    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;

    @Resource
    private CloudResourceMatcherService cloudResourceMatcherService;

    @Resource
    private TopologyServiceCore topologyServiceCore;

    @Resource
    private CloudService cloudService;

    public DeploymentSetup get(ApplicationVersion version, ApplicationEnvironment environment) {
        return alienDAO.findById(DeploymentSetup.class, generateId(version.getId(), environment.getId()));
    }

    public DeploymentSetup getOrFail(ApplicationVersion version, ApplicationEnvironment environment) {
        DeploymentSetup setup = get(version, environment);
        if (setup == null) {
            throw new NotFoundException("No setup found for version [" + version.getId() + "] and environment [" + environment.getId() + "]");
        } else {
            return setup;
        }
    }

    public DeploymentSetup create(ApplicationVersion version, ApplicationEnvironment environment) {
        DeploymentSetup deploymentSetup = new DeploymentSetup();
        deploymentSetup.setId(generateId(version.getId(), environment.getId()));
        deploymentSetup.setEnvironmentId(environment.getId());
        deploymentSetup.setVersionId(version.getId());
        alienDAO.save(deploymentSetup);
        return deploymentSetup;
    }

    /**
     * Try to generate resources mapping for deployment setup from the topology and the cloud.
     * If no value has been chosen this method will generate default value.
     * If existing configuration is no longer valid for the topology and the cloud, this method will correct incompatibility
     * 
     * @param deploymentSetup the deployment setup to generate configuration for
     * @param topology the topology
     * @param cloud the cloud
     * @param automaticSave automatically save the deployment setup if it has been changed
     * @return true if the topology's deployment setup is valid (all resources are matchable), false otherwise
     */
    public boolean generateCloudResourcesMapping(DeploymentSetup deploymentSetup, Topology topology, Cloud cloud, boolean automaticSave) {
        boolean changed = false;
        CloudResourceMatcherConfig cloudResourceMatcherConfig = cloudService.findCloudResourceMatcherConfig(cloud);
        Map<String, IndexedNodeType> types = topologyServiceCore.getIndexedNodeTypesFromTopology(topology, false, true);
        Map<String, List<ComputeTemplate>> matchResult = cloudResourceMatcherService.matchTopology(topology, cloud, cloudResourceMatcherConfig, types)
                .getMatchResult();
        // Generate default matching for deployment setup
        Map<String, ComputeTemplate> cloudResourcesMapping = deploymentSetup.getCloudResourcesMapping();
        boolean valid = true;
        for (Map.Entry<String, List<ComputeTemplate>> matchResultEntry : matchResult.entrySet()) {
            valid = valid && (matchResultEntry.getValue() != null && !matchResultEntry.getValue().isEmpty());
        }
        if (cloudResourcesMapping == null) {
            changed = true;
            cloudResourcesMapping = Maps.newHashMap();
        } else {
            // Try to remove unknown mapping from existing config
            Iterator<Map.Entry<String, ComputeTemplate>> mappingEntryIterator = cloudResourcesMapping.entrySet().iterator();
            while (mappingEntryIterator.hasNext()) {
                Map.Entry<String, ComputeTemplate> entry = mappingEntryIterator.next();
                if (topology.getNodeTemplates() == null || !topology.getNodeTemplates().containsKey(entry.getKey()) || !matchResult.containsKey(entry.getKey())
                        || !matchResult.get(entry.getKey()).contains(entry.getValue())) {
                    // Remove the mapping if topology do not contain the node with that name and of type compute
                    // Or the mapping do not exist anymore in the match result
                    changed = true;
                    mappingEntryIterator.remove();
                }
            }
        }
        for (Map.Entry<String, List<ComputeTemplate>> entry : matchResult.entrySet()) {
            if (!entry.getValue().isEmpty() && !cloudResourcesMapping.containsKey(entry.getKey())) {
                // Only take the first element as selected if no configuration has been set before
                changed = true;
                cloudResourcesMapping.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        deploymentSetup.setCloudResourcesMapping(cloudResourcesMapping);
        if (changed && automaticSave) {
            alienDAO.save(deploymentSetup);
        }
        return valid;
    }

    /**
     * Try to generate a default deployment properties
     * 
     * @param deploymentSetup the deployment setup to generate configuration for
     * @param cloud the cloud
     */
    public void generatePropertyDefinition(DeploymentSetup deploymentSetup, Cloud cloud) {
        Map<String, PropertyDefinition> propertyDefinitionMap = cloudService.getDeploymentPropertyDefinitions(cloud.getId());
        if (propertyDefinitionMap != null) {
            // Reset deployment properties as it might have changed between cloud
            Map<String, String> propertyValueMap = Maps.newHashMap();
            for (Map.Entry<String, PropertyDefinition> propertyDefinitionEntry : propertyDefinitionMap.entrySet()) {
                propertyValueMap.put(propertyDefinitionEntry.getKey(), propertyDefinitionEntry.getValue().getDefault());
            }
            deploymentSetup.setProviderDeploymentProperties(propertyValueMap);
        }
    }

    public String generateId(String versionId, String environmentId) {
        return versionId + "::" + environmentId;
    }

    /**
     * Delete a deployment setup based on the id of the related environment.
     *
     * @param environmentId The id of the environment
     */
    public void deleteByEnvironmentId(String environmentId) {
        alienDAO.delete(DeploymentSetup.class, QueryBuilders.termQuery("environmentId", environmentId));
    }

    /**
     * Delete a deployment setup based on the id of the related version.
     *
     * @param environmentId The id of the version
     */
    public void deleteByVersionId(String environmentId) {
        alienDAO.delete(DeploymentSetup.class, QueryBuilders.termQuery("versionId", environmentId));
    }
}
