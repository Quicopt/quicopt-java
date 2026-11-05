package routing;

/**
 * A place a truck drives to: the depot or a customer. There is one object per place, shared by
 * every stop and vehicle there.
 *
 * @param index row and column in the travel-time tables; 0 is the depot
 */
public record Location(int index) {
}
