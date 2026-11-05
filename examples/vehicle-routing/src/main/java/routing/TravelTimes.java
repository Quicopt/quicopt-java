package routing;

/**
 * Drive times between all locations for one kind of truck. A truck with a trailer takes other roads
 * than a truck alone, so each kind has its own table. Every vehicle of a kind shares one object.
 *
 * <p>Rules read a drive time through {@link #drive}, never from the array. The client can't send an
 * array read to the service. It calls {@code drive} for every pair of locations before the solve
 * instead, and sends the results as a table.
 *
 * @param seconds drive seconds, indexed by {@link Location#index()}
 */
public record TravelTimes(long[][] seconds) {

    /**
     * @param from where the drive starts
     * @param to where the drive ends
     * @return the drive in seconds
     */
    public long drive(Location from, Location to) {
        return seconds[from.index()][to.index()];
    }
}
