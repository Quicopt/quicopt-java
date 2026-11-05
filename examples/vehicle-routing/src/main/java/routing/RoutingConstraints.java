package routing;

import com.quicopt.planner.api.score.HardMediumSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

/**
 * The rules of the delivery day.
 *
 * <p>A plan's score has three levels. Hard counts broken rules, medium counts customers left out,
 * and soft is minus the total drive time. The solver compares hard first, then medium, then soft.
 */
public class RoutingConstraints implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                capacity(factory),
                timeWindow(factory),
                dutyDay(factory),
                trailerAccess(factory),
                reloadLimit(factory),
                everyCustomerServed(factory),
                drive(factory)
        };
    }

    /** No trip carries more than the truck holds. */
    Constraint capacity(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalize(HardMediumSoftScore.ONE_HARD, Vehicle::excessLoad)
                .asConstraint("capacity");
    }

    /** No service starts after the customer's latest start. */
    Constraint timeWindow(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalize(HardMediumSoftScore.ONE_HARD, Vehicle::latenessSeconds)
                .asConstraint("time window");
    }

    /** No working day is longer than the driver may work. */
    Constraint dutyDay(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalize(HardMediumSoftScore.ONE_HARD, Vehicle::excessDutySeconds)
                .asConstraint("duty day");
    }

    /** No truck with a trailer visits a customer it can't reach. */
    Constraint trailerAccess(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalize(HardMediumSoftScore.ONE_HARD, Vehicle::trailerViolations)
                .asConstraint("trailer access");
    }

    /** No truck reloads more often than it may. */
    Constraint reloadLimit(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalize(HardMediumSoftScore.ONE_HARD, Vehicle::excessReloads)
                .asConstraint("reload limit");
    }

    /** Every customer is on some route. Reloads may stay unused. */
    Constraint everyCustomerServed(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Stop.class)
                .filter(stop -> !stop.isReload() && stop.getVehicle() == null)
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("customer served");
    }

    /** The objective: as little driving as possible. */
    Constraint drive(ConstraintFactory factory) {
        return factory.forEach(Vehicle.class)
                .penalize(HardMediumSoftScore.ONE_SOFT, Vehicle::driveSeconds)
                .asConstraint("drive");
    }
}
