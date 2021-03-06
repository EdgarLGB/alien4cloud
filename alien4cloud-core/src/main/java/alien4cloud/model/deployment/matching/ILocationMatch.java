package alien4cloud.model.deployment.matching;

import alien4cloud.model.orchestrators.Orchestrator;
import alien4cloud.model.orchestrators.locations.Location;

public interface ILocationMatch {

    /** Matched location */
    public Location getLocation();

    /** Related orchestrator */
    public Orchestrator getOrchestrator();

    /** Reasons why this location is elected */
    public Object getReasons();

    /**
     * Determines if the location is ready to be deploying on.
     * For example, a location managed by an disabled orchestrator can be eligible, but it is not ready for deployment.
     * 
     * @return true if ready, false if not.
     */
    public boolean isReady();
}
