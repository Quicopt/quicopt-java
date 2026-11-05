package routing;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.variable.InverseRelationShadowVariable;

/**
 * An entry in a truck's route: a customer, or a reload. A reload is a return to the depot. It ends
 * one trip and starts the next with an empty truck.
 *
 * <p>A stop knows the vehicle whose route holds it, so that a rule can find the customers no route
 * holds.
 */
@PlanningEntity
public class Stop {

    /** Window bound that means "no latest start". */
    public static final long OPEN = Long.MAX_VALUE;

    @PlanningId
    private String id;
    private Location location;
    private long demand;
    private long serviceSeconds;
    private long earliestStart;
    private long latestStart;
    private boolean trailerAccess;
    private boolean reload;

    @InverseRelationShadowVariable(sourceVariableName = "stops")
    private Vehicle vehicle;

    /** No-arg constructor, for the copies the solver makes. */
    public Stop() {
    }

    private Stop(String id, Location location, long demand, long serviceSeconds, long earliestStart,
            long latestStart, boolean trailerAccess, boolean reload) {
        this.id = id;
        this.location = location;
        this.demand = demand;
        this.serviceSeconds = serviceSeconds;
        this.earliestStart = earliestStart;
        this.latestStart = latestStart;
        this.trailerAccess = trailerAccess;
        this.reload = reload;
    }

    /**
     * Creates the visit to a customer.
     *
     * @param id the stop's name
     * @param customer the customer
     * @param location where the customer is
     * @return the stop
     */
    public static Stop visit(String id, Day.Customer customer, Location location) {
        long latest = customer.latestStart() == null ? OPEN : customer.latestStart();
        return new Stop(id, location, customer.demand(), customer.serviceSeconds(), customer.earliestStart(),
                latest, customer.trailerAccess(), false);
    }

    /**
     * Creates a reload: a stop at the depot with no demand, no service time and no window.
     *
     * @param id the stop's name
     * @param depot where the depot is
     * @return the stop
     */
    public static Stop reload(String id, Location depot) {
        return new Stop(id, depot, 0, 0, 0, OPEN, true, true);
    }

    /** @return the stop's name */
    public String getId() {
        return id;
    }

    /** @return where the stop is; the depot for a reload */
    public Location location() {
        return location;
    }

    /** @return space the delivery takes on the truck */
    public long demand() {
        return demand;
    }

    /** @return time spent at the stop, in seconds */
    public long serviceSeconds() {
        return serviceSeconds;
    }

    /** @return earliest start of service, in seconds */
    public long earliestStart() {
        return earliestStart;
    }

    /** @return latest start of service, or {@link #OPEN} */
    public long latestStart() {
        return latestStart;
    }

    /** @return whether a truck with a trailer can reach the stop; always true for a reload */
    public boolean trailerAccess() {
        return trailerAccess;
    }

    /** @return whether the stop is a reload rather than a customer */
    public boolean isReload() {
        return reload;
    }

    /** @return the vehicle whose route holds the stop, or {@code null} if no route does */
    public Vehicle getVehicle() {
        return vehicle;
    }

    /** @param vehicle the vehicle whose route now holds the stop */
    public void setVehicle(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    @Override
    public String toString() {
        return id;
    }
}
