## PT2MATSim

A package to convert input schedule data such as GTFS , HAFAS or OSM to a completely mapped MATSim schedule.

The implementation allows to first create a multi-modal network from OSM and then to map the schedule data onto that network.
Tools to validate and edit the mapped schedule are also part of the package.


### Standalone Usage

The tools can be used as a maven package or as a standalone utility. In order to create
a standalone version the repository needs to be cloned. Afterwards, maven can be used
to create a runnable jar:

    mvn -Prelease -DskipTests=true

The runnable tools are then located in

    pt2matsim/target/pt2matsim-VERSION-release.zip

The zip file contains the runnable jar and its dependencies in the `lib/` folder.
The tools can then be run in the following way:

    java -cp pt2matsim-VERSION.jar:libs org.matsim.pt2matsim.run.Gtfs2TransitSchedule


## GTFS Converter

The GTFS converter's main job is transforming GTFS trips to MATSim transit routes. Trips and transit routes are not
equivalent: A trip defines the stop sequence and the departure time for a vehicle while a MATSim transit route defines
the stop sequence but usually has more than one departure time. The time offsets between stops is stored in the
routeProfile within the transit route in MATSim. GTFS offers two ways: Either with explicit time stamps in
stop_times.txt or as frequencies in frequencies.txt. The converter can use both. Finally, routes in GTFS are like
transit lines in MATSim. Stops are converted straight to stop facilities. GTFS offers no explicit information that could
be used as isBlocking value for MATSim stop facilities, so false is set by default.

For each trip, a service id defines on which days the trip is carried out. MATSim normally only simulates one day,
therefore one sample day has to be extracted from the GTFS feed. Although possible, it is not recommended to use
all trips in the feed. The following methods are suggested:

- Define the date to extract all affected trips from it.
- Use the day on which most services run.
- Use the day on which most trips are carried out.

The converter can be run by calling the main() method of

    org.matsim.pt2matsim.run.Gtfs2TransitSchedule

with the following arguments:

    [0] folder where the gtfs files are located (a single zip file is not supported)
    [1]	Which service ids should be used. One of the following:
    	- date in the format "yyyymmdd"
    	- "dayWithMostServices"
    	- "dayWithMostTrips"
    	- "all"
    [2] the output coordinate system. Use "WGS84" for no transformation. EPGS:* codes are supported.
    [3] output transit schedule file
    [4] output default vehicles file (optional)
    [5] output shape reference file. A CSV file that references transit routes to shape ids. Can
        be used by certain PTMapper implementations and MappingAnalysis (optional)
