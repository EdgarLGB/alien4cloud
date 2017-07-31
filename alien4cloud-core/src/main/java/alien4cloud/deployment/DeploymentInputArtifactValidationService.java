package alien4cloud.deployment;

import static alien4cloud.utils.AlienUtils.safe;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.alien4cloud.alm.deployment.configuration.model.DeploymentInputs;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import alien4cloud.topology.task.ArtifactTaskCode;
import alien4cloud.topology.task.InputArtifactTask;

@Service
public class DeploymentInputArtifactValidationService {
    public List<InputArtifactTask> validate(Optional<DeploymentInputs> deploymentInputs) {
        Map<String, DeploymentArtifact> inputArtifacts = getInputArtifacts(deploymentInputs);
        return safe(inputArtifacts).entrySet().stream()
                .filter(deploymentArtifactEntry -> StringUtils.isBlank(deploymentArtifactEntry.getValue().getArtifactRef())
                        && !safe(inputArtifacts).containsKey(deploymentArtifactEntry.getKey()))
                .map(deploymentArtifactEntry -> new InputArtifactTask(deploymentArtifactEntry.getKey(), ArtifactTaskCode.MISSING)).collect(Collectors.toList());
    }

    private Map<String, DeploymentArtifact> getInputArtifacts(Optional<DeploymentInputs> inputsOptional) {
        if (inputsOptional.isPresent()) {
            return inputsOptional.get().getInputArtifacts();
        }
        return null;
    }
}
