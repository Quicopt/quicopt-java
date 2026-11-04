# quicopt-planner

A planning solver for Java. Timetables, rotas and delivery rounds are planning
problems: each needs a plan that keeps a set of rules. You describe the problem in
ordinary Java classes with a few annotations, and you write its rules in Java as
well. The client sends both to the Quicopt service, and the service searches for
the best plan. The client then writes that plan into your objects.

## Installation

The client needs Java 21 or newer, and Maven to build it. Until the client is on
Maven Central, you build it from this repository, and Maven puts it into your
local Maven repository:

```bash
git clone https://github.com/Quicopt/quicopt-java.git
cd quicopt-java
git checkout v0.1.0-beta.1
mvn install
```

The build runs the client's tests, which need no connection to the service. Then
add the client to your project. With Maven:

```xml
<dependency>
    <groupId>com.quicopt</groupId>
    <artifactId>quicopt-planner</artifactId>
    <version>0.1.0-beta.1</version>
</dependency>
```

With Gradle, list your local Maven repository among the repositories:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("com.quicopt:quicopt-planner:0.1.0-beta.1")
}
```

The client reports warnings through Java's built-in logging, so they reach the
console without any setup. One of them tells you when the service stops a search
earlier than your configuration allows.

The service answers only requests that carry an API key. Register at
<https://quicopt.com/signup> to receive one by email. Then put the key into the
environment variable `QUICOPT_API_KEY`, or into the system property
`quicopt.apiKey`.

## A first plan

Six meetings need a time and a room on Monday morning. There are three one-hour
slots and three rooms of different sizes. Rooms and slots are plain data, and
planning does not change them:

```java
public record Room(String name, int capacity) {}

public record Slot(String start) {}
```

The service plans the meetings. `@PlanningEntity` marks their class, and the
service fills in its two `@PlanningVariable` fields, `slot` and `room`, which start
out empty. `@PlanningId` tells meetings apart. The class needs a constructor without
arguments, because the client makes copies of the plan.

```java
import com.quicopt.planner.api.domain.common.PlanningId;
import com.quicopt.planner.api.domain.entity.PlanningEntity;
import com.quicopt.planner.api.domain.variable.PlanningVariable;

@PlanningEntity
public class Meeting {
    @PlanningId private int id;
    private String organizer;
    private int attendees;

    @PlanningVariable private Slot slot;
    @PlanningVariable private Room room;

    public Meeting() {}

    public Meeting(int id, String organizer, int attendees) {
        this.id = id;
        this.organizer = organizer;
        this.attendees = attendees;
    }

    public int getId() { return id; }
    public String getOrganizer() { return organizer; }
    public int getAttendees() { return attendees; }
    public Slot getSlot() { return slot; }
    public Room getRoom() { return room; }

    @Override
    public String toString() {
        return slot.start() + "  " + room.name() + ": " + organizer + ", " + attendees + " people";
    }
}
```

One more class holds the whole plan, and `@PlanningSolution` marks it. The slots
and the rooms are data that the rules read (`@ProblemFactCollectionProperty`) and
values that a meeting can take (`@ValueRangeProvider`).
`@PlanningEntityCollectionProperty` marks the list of meetings, and `@PlanningScore`
marks the field that receives the score.

```java
import java.util.List;

import com.quicopt.planner.api.domain.solution.PlanningEntityCollectionProperty;
import com.quicopt.planner.api.domain.solution.PlanningScore;
import com.quicopt.planner.api.domain.solution.PlanningSolution;
import com.quicopt.planner.api.domain.solution.ProblemFactCollectionProperty;
import com.quicopt.planner.api.domain.valuerange.ValueRangeProvider;
import com.quicopt.planner.api.score.HardSoftScore;

@PlanningSolution
public class MorningPlan {
    @ProblemFactCollectionProperty @ValueRangeProvider private List<Slot> slots;
    @ProblemFactCollectionProperty @ValueRangeProvider private List<Room> rooms;
    @PlanningEntityCollectionProperty private List<Meeting> meetings;
    @PlanningScore private HardSoftScore score;

    public MorningPlan() {}

    public MorningPlan(List<Slot> slots, List<Room> rooms, List<Meeting> meetings) {
        this.slots = slots;
        this.rooms = rooms;
        this.meetings = meetings;
    }

    public List<Slot> getSlots() { return slots; }
    public List<Room> getRooms() { return rooms; }
    public List<Meeting> getMeetings() { return meetings; }
    public HardSoftScore getScore() { return score; }
}
```

Each plan gets a score, which counts the rules that the plan breaks. A **hard**
rule must hold in any usable plan, and a **soft** rule should hold as far as
possible. The score is written like `-2hard/-15soft`. A plan with fewer hard
penalties beats one with more, whatever their soft parts.

Each rule selects the meetings that break it and sets a cost for each:

- **Room conflict** (hard): two meetings in the same room at the same time.
- **Organizer conflict** (hard): one person organizing two meetings at the same
  time.
- **Room too small** (hard): one point for every attendee without a seat.
- **Empty seats** (soft): one point for every seat that a meeting leaves empty, so
  that small meetings stay out of the big rooms.

In code, each rule is a stream over the meetings. `forEach` streams single
meetings, and `forEachUniquePair` streams every pair of them once. `equal` keeps
the pairs that agree on a value, and `filter` keeps the meetings that break the
rule. `penalize` sets the cost of each match, and `asConstraint` names the rule. A `ConstraintProvider`
lists the rules.

```java
import static com.quicopt.planner.api.score.stream.Joiners.equal;

import com.quicopt.planner.api.score.HardSoftScore;
import com.quicopt.planner.api.score.stream.Constraint;
import com.quicopt.planner.api.score.stream.ConstraintFactory;
import com.quicopt.planner.api.score.stream.ConstraintProvider;

public class MeetingRules implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            roomConflict(factory),
            organizerConflict(factory),
            roomTooSmall(factory),
            emptySeats(factory)
        };
    }

    Constraint roomConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Meeting.class, equal(Meeting::getSlot), equal(Meeting::getRoom))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room conflict");
    }

    Constraint organizerConflict(ConstraintFactory factory) {
        return factory.forEachUniquePair(Meeting.class, equal(Meeting::getSlot), equal(Meeting::getOrganizer))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Organizer conflict");
    }

    Constraint roomTooSmall(ConstraintFactory factory) {
        return factory.forEach(Meeting.class)
                .filter(meeting -> meeting.getAttendees() > meeting.getRoom().capacity())
                .penalize(HardSoftScore.ONE_HARD, meeting -> meeting.getAttendees() - meeting.getRoom().capacity())
                .asConstraint("Room too small");
    }

    Constraint emptySeats(ConstraintFactory factory) {
        return factory.forEach(Meeting.class)
                .filter(meeting -> meeting.getRoom().capacity() > meeting.getAttendees())
                .penalize(HardSoftScore.ONE_SOFT, meeting -> meeting.getRoom().capacity() - meeting.getAttendees())
                .asConstraint("Empty seats");
    }
}
```

The program sets up the morning and solves it. Ada, Grace and Edsger each organize
one small and one larger meeting. The solver configuration names the classes of
the plan, the meetings and the rules, and it gives the service five seconds to
search.

```java
import java.time.Duration;
import java.util.List;

import com.quicopt.planner.api.solver.SolverFactory;
import com.quicopt.planner.config.solver.SolverConfig;

public class PlanMorning {

    public static void main(String[] args) {
        var slots = List.of(new Slot("09:00"), new Slot("10:00"), new Slot("11:00"));
        var rooms = List.of(new Room("Huddle", 4), new Room("Board", 10), new Room("Hall", 30));
        var meetings = List.of(
                new Meeting(1, "Ada", 3), new Meeting(2, "Ada", 8),
                new Meeting(3, "Grace", 4), new Meeting(4, "Grace", 9),
                new Meeting(5, "Edsger", 2), new Meeting(6, "Edsger", 6));

        var config = new SolverConfig()
                .withSolutionClass(MorningPlan.class)
                .withEntityClasses(Meeting.class)
                .withConstraintProviderClass(MeetingRules.class)
                .withTerminationSpentLimit(Duration.ofSeconds(5));
        var solver = SolverFactory.<MorningPlan> create(config).buildSolver();

        MorningPlan plan = solver.solve(new MorningPlan(slots, rooms, meetings));
        System.out.println("score: " + plan.getScore());
        plan.getMeetings().forEach(System.out::println);
    }
}
```

With your key in `QUICOPT_API_KEY`, `PlanMorning` prints the plan:

```
score: 0hard/-10soft
09:00  Huddle: Ada, 3 people
10:00  Board: Ada, 8 people
10:00  Huddle: Grace, 4 people
11:00  Board: Grace, 9 people
11:00  Huddle: Edsger, 2 people
09:00  Board: Edsger, 6 people
```

No rule is broken, and ten seats stay empty. Every meeting sits in the smallest
room that holds it. The three larger meetings are in Board, the three small ones
in Huddle, and the Hall stays free. No plan can leave fewer seats empty, so none
scores better. The hours can differ from run to run, because the rules only ask that no
room and no organizer is booked twice in the same hour.
