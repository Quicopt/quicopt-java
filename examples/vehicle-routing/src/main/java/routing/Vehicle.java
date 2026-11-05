package routing;

import java.util.ArrayList;
import java.util.List;

import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.variable.PlanningListVariable;

/**
 * One truck's day: it leaves the depot, serves its stops in order and returns. Reloads in the route
 * split the day into trips.
 *
 * <p>The solver decides the route, the ordered list of stops. Each rule measures the route through
 * one method below. A method walks the route on its own: the client can't send the service an
 * object that holds several measures, so the methods can't share a walk.
 */
@PlanningEntity
public class Vehicle {

    @PlanningId
    private int id;
    private String name;
    private boolean trailer;
    private long capacity;
    private long maxDutySeconds;
    private int maxReloads;
    private TravelTimes travel;
    private Location depot;

    @PlanningListVariable(allowsUnassignedValues = true)
    private List<Stop> stops = new ArrayList<>();

    /** No-arg constructor, for the copies the solver makes. */
    public Vehicle() {
    }

    /**
     * Creates a truck with an empty route.
     *
     * @param id the truck's number
     * @param truck the truck as stored in the day's file
     * @param travel drive times for this kind of truck
     * @param depot where every trip starts and ends
     */
    public Vehicle(int id, Day.Truck truck, TravelTimes travel, Location depot) {
        this.id = id;
        this.name = truck.name();
        this.trailer = truck.trailer();
        this.capacity = truck.capacity();
        this.maxDutySeconds = truck.maxDutySeconds();
        this.maxReloads = truck.maxReloads();
        this.travel = travel;
        this.depot = depot;
    }

    /** @return the truck's number */
    public int getId() {
        return id;
    }

    /** @return the truck's label */
    public String name() {
        return name;
    }

    /** @return the route, in driving order */
    public List<Stop> getStops() {
        return stops;
    }

    /** @param stops the new route */
    public void setStops(List<Stop> stops) {
        this.stops = stops;
    }

    /** @return the time behind the wheel in seconds, depot to depot */
    public long driveSeconds() {
        long drive = 0;
        Location at = depot;
        for (Stop stop : stops) {
            drive += travel.drive(at, stop.location());
            at = stop.location();
        }
        return drive + travel.drive(at, depot);
    }

    /**
     * The length of the working day: driving, service and waiting for windows to open.
     *
     * <p>The truck may leave the depot later than the start of the day. Leaving later shortens the
     * day by the waiting it saves, as long as every stop still starts before its latest start. The
     * walk starts the truck at 0 and adds up the waiting so far. At each stop with a latest start it
     * also tracks how much later the truck could have left and still be on time there.
     *
     * @return the working day in seconds; 0 for an empty route
     */
    public long durationSeconds() {
        long clock = 0;
        long waitSoFar = 0;                     // waiting before the current stop, all stops so far
        long canLeaveLater = Long.MAX_VALUE;    // how much later the truck could leave and be on time
        Location at = depot;
        for (Stop stop : stops) {
            long arrival = clock + travel.drive(at, stop.location());
            long serviceStart = Math.max(arrival, stop.earliestStart());
            waitSoFar += serviceStart - arrival;
            if (stop.latestStart() != Stop.OPEN) {
                canLeaveLater = Math.min(canLeaveLater, waitSoFar + stop.latestStart() - serviceStart);
            }
            clock = serviceStart + stop.serviceSeconds();
            at = stop.location();
        }
        long end = clock + travel.drive(at, depot);
        return end - Math.max(0, Math.min(waitSoFar, canLeaveLater));
    }

    /** @return seconds of service started after a stop's latest start, summed over the route */
    public long latenessSeconds() {
        long clock = 0;
        long lateness = 0;
        Location at = depot;
        for (Stop stop : stops) {
            long serviceStart = Math.max(clock + travel.drive(at, stop.location()), stop.earliestStart());
            if (stop.latestStart() != Stop.OPEN) {
                lateness += Math.max(0, serviceStart - stop.latestStart());
            }
            clock = serviceStart + stop.serviceSeconds();
            at = stop.location();
        }
        return lateness;
    }

    /** @return load beyond the truck's capacity, summed over its trips */
    public long excessLoad() {
        long load = 0;
        long excess = 0;
        for (Stop stop : stops) {
            if (stop.isReload()) {
                excess += Math.max(0, load - capacity);
                load = 0;
            } else {
                load += stop.demand();
            }
        }
        return excess + Math.max(0, load - capacity);
    }

    /** @return seconds by which the working day is longer than the driver may work */
    public long excessDutySeconds() {
        return Math.max(0, durationSeconds() - maxDutySeconds);
    }

    /** @return customers on the route that a truck with a trailer can't reach, if this truck has one */
    public long trailerViolations() {
        long violations = 0;
        for (Stop stop : stops) {
            if (trailer && !stop.trailerAccess()) {
                violations++;
            }
        }
        return violations;
    }

    /** @return reloads beyond the truck's allowance */
    public long excessReloads() {
        long reloads = 0;
        for (Stop stop : stops) {
            if (stop.isReload()) {
                reloads++;
            }
        }
        return Math.max(0, reloads - maxReloads);
    }

    /** @return whether the route serves at least one customer */
    public boolean isUsed() {
        return stops.stream().anyMatch(stop -> !stop.isReload());
    }

    /** @return the number of trips, i.e. how often the truck leaves the depot loaded */
    public int trips() {
        int trips = 0;
        boolean loaded = false;
        for (Stop stop : stops) {
            if (stop.isReload()) {
                loaded = false;
            } else if (!loaded) {
                trips++;
                loaded = true;
            }
        }
        return trips;
    }

    @Override
    public String toString() {
        return name;
    }
}
