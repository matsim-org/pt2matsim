## PT2MATSim

A package to convert input schedule data such as GTFS , HAFAS or OSM to a completely mapped MATSim schedule.

The implementation allows to first create a multi-modal network from OSM and then to map the schedule data onto that network. Tools to validate and edit the mapped schedule are also part of the package.

### Standalone Usage

The tools can be used as a maven package or as a standalone utility. In order to create
a standalone version the repository needs to be cloned. Afterwards, maven can be used
to create a runnable jar:

    mvn -Prelease -DskipTests=true

The runnable tools are then located in

    pt2matsim/target/pt2matsim-VERSION-release.zip

The zip file contains the runnable jar and its dependencies in the `lib/` folder.
The tools can then be run in the following way:

    java -cp pt2matsim-VERSION.jar:libs org.matsim.pt2matsim.gtfs.Gtfs2TransitSchedule
