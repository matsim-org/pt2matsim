# PT2MATSim

[![Build Status](https://travis-ci.org/matsim-org/pt2matsim.svg?branch=master)](https://travis-ci.org/matsim-org/pt2matsim)

PT2MATSim is a package to convert schedule data such as GTFS, HAFAS or OSM to a completely mapped MATSim schedule.

There are multiple public transit schedule data formats, widely used formats are GTFS and HAFAS. Numerous GTFS feeds are
publicly available (see [transitfeeds.com](https://transitfeeds.com) or [transit.land](http://www.transit.land)), otherwise these files
have to be obtained from the public transit agency. The Swiss public transit schedule is available GTFS and HAFAS format via [opentransportdata.swiss](https://opentransportdata.swiss).
Public transit data feeds can be converted to unmapped MATSim transit schedules using the classes _Gtfs2Transitschedule_ or _Hafas2TransitSchedule_.
It is possible to convert public transit information from OpenStreetMap files (_Osm2TransitSchedule_). However,
OSM currently does not contain any temporal information, the accuracy of the schedule data varies and is
usually not sufficient to be used for simulations.

Unmapped MATSim transit schedule lack information on the links used by vehicles and only contain the stop sequence
for transit routes. Generating these links (i.e. the path a vehicle takes on a network) is called "mapping", a process
done by the _PublicTransitMapper_. It implements an algorithm that uses an abstract graph to calculate the least cost path
from the transit route's first to its last stop with the constraint that the path must contain a so called link candidate
for every stop. The result is a mapped transit schedule and a modified network.

Tools to validate and rudimentarily edit the mapped schedule are also part of the package.

### Package Workflow

![pt2matsim workflow](doc/pt2matsim_workflow.jpg)

### Binaries

Releases are available on Bintray: https://bintray.com/polettif/matsim/pt2matsim. Run the _-shaded.jar_ for standalone usage.

To include pt2matsim in your own maven project add this snippet to your pom.xml:

    <repositories>
        <repository>
            <id>pt2matsim</id>
            <url>http://dl.bintray.com/polettif/matsim</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.matsim</groupId>
            <artifactId>pt2matsim</artifactId>
            <version>18.5</version>
        </dependency>
    </dependencies>

The master branch contains the snapshot version with the latest changes. Clone the git repository to use it.

## Creating a multimodal network

The converter is based on the OSM network by M. Rieser (_org.matsim.core.utils.io.OsmNetworkReader_). However, some changes
have been made: The default values for links as well as parameters are stored in a _config.xml_ file. This file allows to
define whether an opposite link should be created and to set values for capacity, freespeed and the number of lanes
for _highway=*_ and _railway=*_ tags. This also allows to convert rail links. In addition, ways that are only used by
buses (i.e. are part of a relation tagged with _route=*_ in OSM) are included in a network. If information on number of lanes 
or freespeed is available for an OSM way, it is used for the MATSim link. Relevant OSM attributes (for ways and routes) are
stored as link attributes in the network.

It is recommended to simplify the network by inserting only one link instead of multiple links between two intersections. 
The Euclidian distance between the new link's end nodes will less than the the summed up length of the replaced links but 
the original length can be stored in the length attribute of the new link.

A default config file can be created by running

    org.matsim.pt2matsim.run.CreateDefaultOsmConfig defaultConfigFile.xml
    
The converter can be run via

    org.matsim.pt2matsim.run.Osm2MultimodalNetwork
    
with the config file path as argument.   

## Creating an unmapped MATSim transit schedule

### From GTFS

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
    [1] Which service ids should be used. One of the following:
    	- date in the format "yyyymmdd"
    	- "dayWithMostServices"
    	- "dayWithMostTrips"
    	- "all"
    [2] the output coordinate system. Use "WGS84" for no transformation. EPSG:* codes are supported.
    [3] output transit schedule file
    [4] output default vehicles file (optional)

If a feed contains shapes, the shape id is stored in the transit route's description.

### From HAFAS
HAFAS is a data format used by Swiss public transit agencies (particularly SBB) and other agencies in Europe (e.g. Deutsche Bahn).
Data is given in ASCII text files that contain information about operators, stops, vehicles and stop sequences, stop times and
additional information. HAFAS data for Switzerland is publicly available and provided by SBB. A report that
comes with this exported data provides information on the structure of the files.

Stop facilities are created from the stops defined in the file BFKOORD_GEO (uses WGS84). The transit lines, routes, departures and stop sequences
are generated from _FPLAN_. The file _BETRIEB_DE_ is used to add the agency's name to the transit line id. The reference day is
extracted using _BITFELD_. A default transport mode is assigned to each transit route depending on the vehicle defined in
HAFAS. The transport modes correspond to one of the eight transport modes defined in GTFS (see [GTFS reference](https://developers.google.com/transit/gtfs/reference/routes-file)).
Optionally, a default vehicle file is provided using predefined values (based on data provided by M. Rieser and P. Bösch) for different vehicle types 
such as interregional trains, S-Bahn trains or trolley buses.

Converts all files in _hafasFolder_ and writes the output schedule and vehicle file. Stop Facility coordinates are transformed to _outputCoordinateSystem_.

The converter can be run by calling the main() method of

    org.matsim.pt2matsim.run.Hafas2TransitSchedule

with the following arguments:

    [0] hafasFolder
    [1] outputCoordinateSystem. Use "WGS84" for no transformation. EPSG:* codes are supported.
    [2] outputScheduleFile
    [3] outputVehicleFile

### From OSM

OSM offers tags to specify spatial public transit data on stop locations and transit routes. The converter provided as 
part of the package creates an unmapped transit schedule from OSM data. It creates stop facilities from OSM nodes with
the tag _public\_transport=stop\_position_. Relations with the tag _route=*_ are converted to transit routes. These transit 
routes contain only stop sequences. Link sequences are not converted even if they are available. The transport mode is set 
based on the respective _route=*_ tag value of the relation.

The quality of the generated transit schedule for a region depends largely on the accuracy of the data in OSM. Often
route data is either inconsistent or not even available. The lack of naming conventions further complicates using the data. 
In addition, OSM does not offer any tags to store temporal information. Thus, departure times and stop offsets have to be
gathered from other sources.

The converter can be run via

    org.matsim.pt2matsim.run.Osm2TransitSchedule
    
with the following arguments    

    [0] osm file
	[1] output schedule file
	[2] output coordinate system (optional)
    
## Mapping a transit schedule to a network

### Public Transit Mapper

All input parameters are defined in a config file, a default config file can be created via
    
    org.matsim.pt2matsim.run.CreateDefaultPTMapperConfig
    
The whole schedule is mapped to the network. In cases where only subsets should be used (e.g. for a region), 
the schedule needs to be filtered beforehand.

The Public Transit Mapper is run via

    org.matsim.pt2matsim.run.PublicTransitMapper

with a config file as input argument.

[The project wiki explains how the schedule is mapped to the network.](https://github.com/matsim-org/pt2matsim/wiki/Mapping-algorithm)

### Check Mapped Schedule Plausibility
While the TransitScheduleValidator is a good first indicator of the quality of a schedule, 
it does not check how plausible a transit route is. Possible implausibilities are for example if a 
route has loops, if sudden direction changes happen between links (u-turns) or if the travel times given 
by the schedule are cannot be met. If the travel time between two stops is higher than the scheduled time 
this points to either a too long (i.e. wrong) route or too low freespeed values on the network.

The package provides a plausibility check via 

    org.matsim.pt2matsim.run.CheckMappedSchedulePlausibility
    
that looks for implausible parts of a route and warns accordingly. It needs the input:
	
	[0] schedule file
	[1] network file
	[2] coordinate system (of both schedule and network)
	[3] output folder

The following files are created in the output folder:

- _allPlausibilityWarnings.csv_: shows all plausibility warnings in a csv file
- _stopfacilities.csv_: the number of child stop facilities for all stop facilities as csv
- _stopfacilities_histogram.png_: a histogram as png showing the number of child stop facilities
- _plausibilityWarnings.geojson_: Contains all warnings for groups of links
- _schedule_transitRoutes.geojson_: Transit routes of the schedule as lines
- _schedule_stopFacilities.geojson_: Stop Facilities as points
- _schedule_stopFacilities_refLinks.geojson_: The stop facilities' reference links as lines
- _network.geojson_: Network as geojson file containing nodes and links

Geojson files can be viewed in a GIS, a recommended open source application is [QGIS](https://www.qgis.org). It
allows drag and drop loading and viewing of the plausibility results, network and schedule files.

#### Travel Time Warning
Warns if the travel time given by the schedule cannot be achieved by a transit route. This indicates that the
network's freespeed values or link lengths are not suitable for the given transit route. A warning can also point
to large detours due to one-way or missing links.

#### Artificial Link Warning
Warns if a link is an artificial link (i.e. created by the pt mapper). To prevent these warnings, edit
the network before pt mapping in a way that every stop - that should not use artificial links - has a feasible
link nearby.

#### Loop warning
Warns if a transit route's link sequence passes a node twice. This might be intended behaviour if a route really has
loops. Otherwise it might indicate detours due to wrongly chosen link candidates.

#### Direction Change Warning
Warns if a link sequence has abrupt direction changes. This means by default >60° for bus and >30° for rail.
Whether they point to real problems depends on network accuracy. Abrupt direction changes in itself do not impact
a simulation.
