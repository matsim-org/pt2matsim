# PT2MATSim

[![Status Maven Test](https://github.com/matsim-org/pt2matsim/actions/workflows/maven-test.yml/badge.svg)](https://github.com/matsim-org/pt2matsim/actions/workflows/maven-test.yml)

PT2MATSim is a package to convert public transit data from GTFS, HAFAS or OSM to a completely mapped MATSim schedule.

![Mapping example](doc/mapping-example.png)

There are multiple public transit schedule data formats, widely used formats are [GTFS](https://gtfs.org/) and HAFAS. Numerous GTFS feeds are
publicly available (see [mobilitydatabase.org](https://mobilitydatabase.org) or [transit.land](http://www.transit.land)), otherwise 
these files have to be obtained from the public transit agency.[^1] 

[^1]: The Swiss public transit schedule is available in GTFS and HAFAS format via [opentransportdata.swiss](https://opentransportdata.swiss).

Public transit data feeds can be converted to unmapped transit schedules using the classes [`Gtfs2Transitschedule`](https://github.com/matsim-org/pt2matsim/wiki/Creating-an-unmapped-MATSim-transit-schedule#from-gtfs)
or [`Hafas2TransitSchedule`](https://github.com/matsim-org/pt2matsim/wiki/Creating-an-unmapped-MATSim-transit-schedule#from-hafas).[^2] 
PT2MATSim also provides tools to create a multimodal network from OSM with [`Osm2MultimodalNetwork`](https://github.com/matsim-org/pt2matsim/wiki/Creating-a-multimodal-network-(Osm2MultimodalNetwork)).

[^2]: It is possible to convert public transit information from OpenStreetMap files (_Osm2TransitSchedule_). 
However, OSM currently does not contain any temporal information, the accuracy of the schedule data varies and is usually not sufficient to be used for simulations.

Unmapped transit schedules lack information on the links used by vehicles and only contain the stop sequence
for transit routes. Generating these links (i.e. the path a vehicle takes on a network) is called "mapping", a process
done by the [`PublicTransitMapper`](https://github.com/matsim-org/pt2matsim/wiki/Mapping-a-MATSim-schedule-to-a-MATSim-network-(Public-Transit-Mapper)). 
It [implements an algorithm](https://github.com/matsim-org/pt2matsim/wiki/PTMapper-algorithm-and-config-parameters) 
that uses an abstract graph to calculate the least cost path for a transit route with the constraint 
that the path must contain a so called link candidate for every stop. The result is a mapped transit schedule and a modified network.

Tools to validate and rudimentarily edit the mapped schedule are also part of the package.

### Package Workflow

[The wiki contains information on how to run the converters and mappers.](https://github.com/matsim-org/pt2matsim/wiki)

![pt2matsim workflow](doc/pt2matsim_workflow.jpg)

### Binaries

Releases are available on [repo.matsim.org](https://repo.matsim.org/service/rest/repository/browse/matsim/org/matsim/pt2matsim/). Run the _-shaded.jar_ for standalone usage.

To include pt2matsim in your own maven project, add this snippet to your pom.xml:

    <repositories>
        <repository>
            <id>pt2matsim</id>
            <url>https://repo.matsim.org/repository/matsim/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.matsim</groupId>
            <artifactId>pt2matsim</artifactId>
            <version>26.1</version>
        </dependency>
    </dependencies>

The master branch contains the snapshot version with the latest changes. Clone the git repository to use it.
