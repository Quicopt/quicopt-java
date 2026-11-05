package routing;

import java.util.List;
import java.util.Map;

/**
 * One weekday's problem, as stored in {@code data/day-<n>.json}.
 *
 * <p>Times are in seconds, and window bounds count from the start of the day. Demand and capacity
 * are in hundredths of a roll container. In {@code travelSeconds}, location 0 is the depot and
 * customer {@code i} (counted from 0) is location {@code i + 1}.
 *
 * @param day the weekday, 1 to 5
 * @param customers the customers to serve
 * @param vehicles the trucks available
 * @param travelSeconds drive times between all locations, {@code "solo"} for a truck alone and
 *     {@code "trailer"} for a truck with a trailer
 */
public record Day(int day, List<Customer> customers, List<Truck> vehicles, Map<String, long[][]> travelSeconds) {

    /**
     * A customer to serve.
     *
     * @param demand space the delivery takes on the truck
     * @param serviceSeconds time spent at the customer
     * @param earliestStart earliest start of service
     * @param latestStart latest start of service, or {@code null} if there is none
     * @param trailerAccess whether a truck with a trailer can reach the customer
     */
    public record Customer(long demand, long serviceSeconds, long earliestStart, Long latestStart,
            boolean trailerAccess) {
    }

    /**
     * A truck and its driver for the day.
     *
     * @param name the truck's label
     * @param trailer whether the truck pulls a trailer today
     * @param capacity space on the truck per trip
     * @param maxDutySeconds longest working day: driving, service and waiting
     * @param maxReloads how often the truck may return to the depot to load again
     */
    public record Truck(String name, boolean trailer, long capacity, long maxDutySeconds, int maxReloads) {
    }
}
