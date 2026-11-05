package routing;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quicopt.planner.api.solver.Solver;
import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.SolverConfig;

/**
 * Plans every day given on the command line and prints one line per day. Each day's routes go to
 * {@code plans/day-<n>.json}.
 *
 * <pre>
 * java -jar target/routing.jar &lt;seconds per day&gt; data/day-1.json data/day-2.json ...
 * </pre>
 *
 * <p>The API key is read from {@code QUICOPT_API_KEY}.
 */
public final class Main {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Main() {
    }

    /**
     * @param args the seconds to search per day, then the day files
     * @throws Exception if a day can't be read or its plan can't be written
     */
    public static void main(String[] args) throws Exception {
        Duration perDay = Duration.ofSeconds(Long.parseLong(args[0]));
        SolverConfig config = new SolverConfig()
                .withSolutionClass(RoutePlan.class)
                .withEntityClasses(Vehicle.class, Stop.class)
                .withConstraintProviderClass(RoutingConstraints.class)
                .withTerminationSpentLimit(perDay);
        Solver<RoutePlan> solver = SolverFactory.<RoutePlan> create(config).buildSolver();

        Files.createDirectories(Path.of("plans"));
        long weekDrive = 0;
        for (int a = 1; a < args.length; a++) {   // loop over the day files
            Day day = JSON.readValue(new File(args[a]), Day.class);
            RoutePlan plan = solver.solve(new RoutePlan(day));

            List<Vehicle> used = plan.getVehicles().stream().filter(Vehicle::isUsed).toList();
            int trips = used.stream().mapToInt(Vehicle::trips).sum();
            System.out.printf(Locale.ROOT, "Day %d  %-26s drive %5.2f h  %2d trucks, %2d trips%n", day.day(), plan.getScore(),
                    plan.driveSeconds() / 3600.0, used.size(), trips);
            weekDrive += plan.driveSeconds();
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of("plans", "day-" + day.day() + ".json").toFile(),
                    routes(day, plan));
        }   // end loop over the day files
        System.out.printf(Locale.ROOT, "Week drive %.2f h%n", weekDrive / 3600.0);
    }

    /**
     * Writes a plan's routes as trips of customer numbers, counted from 1 as in the plan's stops.
     *
     * @param day the day's problem
     * @param plan the solved plan
     * @return the day, the score, the drive and one route per truck in use
     */
    static Map<String, Object> routes(Day day, RoutePlan plan) {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (Vehicle vehicle : plan.getVehicles()) {   // loop over the trucks
            if (!vehicle.isUsed()) {
                continue;
            }
            List<List<Integer>> trips = new ArrayList<>();
            List<Integer> trip = new ArrayList<>();
            for (Stop stop : vehicle.getStops()) {   // loop over the truck's stops
                if (stop.isReload()) {
                    if (!trip.isEmpty()) {
                        trips.add(trip);
                    }
                    trip = new ArrayList<>();
                } else {
                    trip.add(stop.location().index());
                }
            }   // end loop over the truck's stops
            if (!trip.isEmpty()) {
                trips.add(trip);
            }
            Map<String, Object> route = new LinkedHashMap<>();
            route.put("truck", vehicle.name());
            route.put("trips", trips);
            route.put("driveSeconds", vehicle.driveSeconds());
            route.put("durationSeconds", vehicle.durationSeconds());
            routes.add(route);
        }   // end loop over the trucks

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("day", day.day());
        out.put("score", plan.getScore().toString());
        out.put("driveSeconds", plan.driveSeconds());
        out.put("routes", routes);
        return out;
    }
}
