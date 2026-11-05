# Vehicle routing

A week of real deliveries, planned with this client. Every weekday, 13 or 14
trucks leave one depot and serve 88 to 105 customers. A plan has to keep five
rules: no trip carries more than its truck holds, some customers have a latest
start of service, a driver's day lasts at most 8 h 45, trucks with a trailer
can't reach most customers, and a truck may return to the depot up to four
times to load again. Of the plans that keep all five, the best one drives the
fewest hours.

The page [Vehicle routing in Java](https://quicopt.com/developer/vehicle-routing-java)
walks through the model.

## The data

`data/day-1.json` to `data/day-5.json` hold one weekday each: the customers,
the trucks, and two tables of drive times in seconds, one for a truck alone and
one for a truck with a trailer. The data are real and anonymised: names,
places and weekdays are gone, and times and demands are perturbed.

## Running it

The client isn't on Maven Central yet. Install the version this example uses,
from the repository's root:

```bash
git checkout v0.1.0-beta.1
mvn install
git checkout main
```

The service needs an API key. Register at <https://quicopt.com/signup> to
receive one by email, and put it into the environment variable
`QUICOPT_API_KEY`. Then, in this directory, build with JDK 21 or later and plan
the week with 20 seconds per day:

```bash
mvn -q package
java -jar target/routing.jar 20 data/day-1.json data/day-2.json data/day-3.json data/day-4.json data/day-5.json
```

The run prints one line per day and writes each day's routes to
`plans/day-<n>.json`.
