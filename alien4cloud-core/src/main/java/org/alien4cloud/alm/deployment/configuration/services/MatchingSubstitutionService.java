package org.alien4cloud.alm.deployment.configuration.services;

import static alien4cloud.utils.AlienUtils.safe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.inject.Inject;

import org.alien4cloud.alm.deployment.configuration.events.OnDeploymentConfigCopyEvent;
import org.alien4cloud.alm.deployment.configuration.flow.EnvironmentContext;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutor;
import org.alien4cloud.alm.deployment.configuration.flow.ITopologyModifier;
import org.alien4cloud.alm.deployment.configuration.flow.modifiers.NodeMatchingConfigAutoSelectModifier;
import org.alien4cloud.alm.deployment.configuration.flow.modifiers.NodeMatchingConfigCleanupModifier;
import org.alien4cloud.alm.deployment.configuration.flow.modifiers.action.SetMatchedNodeModifier;
import org.alien4cloud.alm.deployment.configuration.model.AbstractDeploymentConfig;
import org.alien4cloud.alm.deployment.configuration.model.DeploymentMatchingConfiguration;
import org.alien4cloud.tosca.model.Csar;
import org.alien4cloud.tosca.model.templates.Topology;
import org.apache.commons.collections4.MapUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;

import alien4cloud.application.ApplicationService;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.topology.TopologyServiceCore;

/**
 * Service responsible to configure the matched substitutions for a given deployment.
 */
@Service
public class MatchingSubstitutionService {
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO alienDAO;
    @Inject
    private FlowExecutor flowExecutor;
    @Inject
    private NodeMatchingConfigCleanupModifier nodeMatchingConfigCleanupModifier;
    @Inject
    private NodeMatchingConfigAutoSelectModifier nodeMatchingConfigAutoSelectModifier;
    @Inject
    private TopologyServiceCore topologyServiceCore;
    @Inject
    private ApplicationService applicationService;

    /**
     * Execute the deployment flow with a modification of changing the substitution for one of the nodes.
     *
     * @param application The application for which to execute the deployment flow.
     * @param environment The environment for which to execute the deployment flow.
     * @param topology The topology linked to the specified environment.
     * @param nodeId The id of the node to substitute at matching phase.
     * @param locationResourceTemplateId The id of the location resources to substitute.
     * @return The flow execution context.
     */
    public FlowExecutionContext updateSubstitution(Application application, ApplicationEnvironment environment, Topology topology, String nodeId,
            String locationResourceTemplateId) {
        FlowExecutionContext executionContext = new FlowExecutionContext(alienDAO, topology, new EnvironmentContext(application, environment));
        // Load the actual configuration

        // add a modifier that will actually perform the configuration of a substitution from user request (after cleanup and prior to node matching
        // auto-selection).
        List<ITopologyModifier> modifierList = getModifierListWithSelectionAction(nodeId, locationResourceTemplateId);

        flowExecutor.execute(topology, modifierList, executionContext);
        return executionContext;
    }

    private List<ITopologyModifier> getModifierListWithSelectionAction(String nodeId, String locationResourceTemplateId) {
        List<ITopologyModifier> modifierList = flowExecutor.getDefaultFlowModifiers();

        for (int i = 0; i < modifierList.size(); i++) {
            if (modifierList.get(i) == nodeMatchingConfigAutoSelectModifier) {
                modifierList.add(i + 1, new SetMatchedNodeModifier(nodeId, locationResourceTemplateId));
                return modifierList;
            }
        }

        throw new IllegalArgumentException(
                "Unexpected exception in deployment flow to update node substitution; unable to find the matching config cleanup modifier to inject selection action modifier.");
    }

    @EventListener
    @Order(30) // Process this after location matching copy (first element).
    public void onCopyConfiguration(OnDeploymentConfigCopyEvent onDeploymentConfigCopyEvent) {
        ApplicationEnvironment source = onDeploymentConfigCopyEvent.getSourceEnvironment();
        ApplicationEnvironment target = onDeploymentConfigCopyEvent.getTargetEnvironment();
        DeploymentMatchingConfiguration sourceConfiguration = alienDAO.findById(DeploymentMatchingConfiguration.class,
                AbstractDeploymentConfig.generateId(source.getTopologyVersion(), source.getId()));

        DeploymentMatchingConfiguration targetConfiguration = alienDAO.findById(DeploymentMatchingConfiguration.class,
                AbstractDeploymentConfig.generateId(target.getTopologyVersion(), target.getId()));

        if (sourceConfiguration == null || MapUtils.isEmpty(sourceConfiguration.getLocationGroups()) || targetConfiguration == null
                || MapUtils.isEmpty(targetConfiguration.getLocationGroups())) {
            return; // Nothing to copy
        }

        // We have to execute a piece of the deployment flow to find out matching candidates so we copy only required inputs
        Topology topology = topologyServiceCore.getOrFail(Csar.createId(target.getApplicationId(), target.getTopologyVersion()));

        if (MapUtils.isNotEmpty(topology.getNodeTemplates())) {
            Application application = applicationService.getOrFail(target.getApplicationId());
            FlowExecutionContext executionContext = new FlowExecutionContext(alienDAO, topology, new EnvironmentContext(application, target));
            flowExecutor.execute(topology, getMatchingFlow(), executionContext);

            Map<String, Set<String>> locResTemplateIdsPerNodeIds = (Map<String, Set<String>>) executionContext.getExecutionCache()
                    .get(FlowExecutionContext.MATCHED_LOCATION_RESOURCE_TEMPLATE_IDS_PER_NODE);

            // Update the substitution on the target if available substitution is always compatible
            Map<String, String> validOnNewEnvSubstitutedNodes = sourceConfiguration.getMatchedLocationResources().entrySet().stream()
                    .filter(entry -> locResTemplateIdsPerNodeIds.containsKey(entry.getKey())
                            && locResTemplateIdsPerNodeIds.get(entry.getKey()).contains(entry.getValue()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (MapUtils.isNotEmpty(validOnNewEnvSubstitutedNodes)) {

                if (targetConfiguration.getMatchedLocationResources() == null) {
                    targetConfiguration.setMatchedLocationResources(Maps.newHashMap());
                }

                validOnNewEnvSubstitutedNodes.forEach((key, value) -> {
                    targetConfiguration.getMatchedLocationResources().put(key, value);
                    // Copy properties set on the node to the new one
                    targetConfiguration.getMatchedNodesConfiguration().put(key, safe(sourceConfiguration.getMatchedNodesConfiguration()).get(key));
                });

                alienDAO.save(targetConfiguration);
            }
        }
    }

    private List<ITopologyModifier> getMatchingFlow() {
        List<ITopologyModifier> modifierList = flowExecutor.getDefaultFlowModifiers();

        for (int i = 0; i < modifierList.size(); i++) {
            if (modifierList.get(i) == nodeMatchingConfigAutoSelectModifier) {
                return modifierList.subList(0, i + 1);
            }
        }

        throw new IllegalArgumentException(
                "Unexpected exception in deployment flow to update node substitution; unable to find the matching config cleanup modifier to inject selection action modifier.");
    }
}