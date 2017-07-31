package org.alien4cloud.alm.deployment.configuration.flow.modifiers;

import static alien4cloud.utils.AlienUtils.safe;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.inject.Inject;

import alien4cloud.topology.task.InputArtifactTask;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.ITopologyModifier;
import org.alien4cloud.alm.deployment.configuration.model.DeploymentInputs;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.definitions.PropertyDefinition;
import org.alien4cloud.tosca.model.definitions.PropertyValue;
import org.alien4cloud.tosca.model.templates.Topology;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import alien4cloud.deployment.DeploymentInputArtifactValidationService;
import alien4cloud.topology.task.PropertiesTask;
import alien4cloud.topology.task.TaskCode;
import alien4cloud.topology.task.TaskLevel;

/**
 * Topology modifier that performs input validation
 */
@Component
public class InputValidationModifier implements ITopologyModifier {
    @Inject
    private DeploymentInputArtifactValidationService deploymentInputArtifactValidationService;

    /**
     * Validate all required input is provided with a non null value.
     * 
     * @param topology The topology to process.
     * @param context The object that stores warnings and errors (tasks) associated with the execution flow. Note that the flow will end-up if an error
     */
    @Override
    public void process(Topology topology, FlowExecutionContext context) {
        Optional<DeploymentInputs> inputsOptional = context.getConfiguration(DeploymentInputs.class, InputValidationModifier.class.getSimpleName());
        Map<String, PropertyValue> inputProperties = getInputs(inputsOptional);

        // Define a task regarding properties
        PropertiesTask task = new PropertiesTask();
        task.setCode(TaskCode.INPUT_PROPERTY);
        task.setProperties(Maps.newHashMap());
        task.getProperties().put(TaskLevel.REQUIRED, Lists.newArrayList());
        Map<String, PropertyValue> inputValues = safe(inputProperties);
        for (Entry<String, PropertyDefinition> propDef : safe(topology.getInputs()).entrySet()) {
            if (propDef.getValue().isRequired() && inputValues.get(propDef.getKey()) == null) {
                task.getProperties().get(TaskLevel.REQUIRED).add(propDef.getKey());
            }
        }

        if (CollectionUtils.isNotEmpty(task.getProperties().get(TaskLevel.REQUIRED))) {
            context.log().error(task);
        }

        // Check input artifacts
        List<InputArtifactTask> artifactTasks = deploymentInputArtifactValidationService.validate(inputsOptional);
        artifactTasks.forEach(inputArtifactTask -> context.log().error(inputArtifactTask));
    }

    private Map<String, PropertyValue> getInputs(Optional<DeploymentInputs> inputsOptional) {
        if (inputsOptional.isPresent()) {
            return inputsOptional.get().getInputs();
        }
        return null;
    }

}
