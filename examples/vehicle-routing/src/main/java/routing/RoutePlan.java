package routing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardMediumSoftScore;

/** One weekday's plan: every truck's route over the customers and the reloads. */
@PlanningSolution
public class RoutePlan {

    @PlanningEntityCollectionProperty
    private List<Vehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<Stop> stops;

    @PlanningScore
    private HardMediumSoftScore score;

    /** No-arg constructor, for the copies the solver makes. */
    public RoutePlan() {
    }

    /**
     * Creates the unsolved plan for a day: all routes empty, no stop on any route.
     *
     * <p>Besides one stop per customer, the plan holds a pool of reloads, as many as all trucks
     * together may use. The solver puts a reload into a route where a truck should return to the
     * depot, and leaves the rest of the pool unused.
     *
     * @param day the day's problem
     */
    public RoutePlan(Day day) {
        List<Location> locations = IntStream.rangeClosed(0, day.customers().size()).mapToObj(Location::new).toList();
        Location depot = locations.get(0);
        TravelTimes solo = new TravelTimes(day.travelSeconds().get("solo"));
        TravelTimes withTrailer = new TravelTimes(day.travelSeconds().get("trailer"));

        vehicles = new ArrayList<>();
        for (int v = 0; v < day.vehicles().size(); v++) {   // loop over the trucks
            Day.Truck truck = day.vehicles().get(v);
            vehicles.add(new Vehicle(v, truck, truck.trailer() ? withTrailer : solo, depot));
        }   // end loop over the trucks

        stops = new ArrayList<>();
        for (int c = 0; c < day.customers().size(); c++) {   // loop over the customers
            stops.add(Stop.visit("customer " + (c + 1), day.customers().get(c), locations.get(c + 1)));
        }   // end loop over the customers
        int reloads = day.vehicles().stream().mapToInt(Day.Truck::maxReloads).sum();
        for (int r = 0; r < reloads; r++) {   // loop over the reload pool
            stops.add(Stop.reload("reload " + (r + 1), depot));
        }   // end loop over the reload pool
    }

    /** @return the trucks */
    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    /** @return all customers and reloads */
    public List<Stop> getStops() {
        return stops;
    }

    /** @return the score, or {@code null} before the plan is scored */
    public HardMediumSoftScore getScore() {
        return score;
    }

    /** @param score the new score */
    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }

    /** @return total drive in seconds over all routes */
    public long driveSeconds() {
        return vehicles.stream().mapToLong(Vehicle::driveSeconds).sum();
    }
}
