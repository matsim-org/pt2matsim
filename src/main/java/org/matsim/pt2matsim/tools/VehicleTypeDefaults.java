/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.pt2matsim.tools;

import org.matsim.vehicles.VehicleType;

import static org.matsim.pt2matsim.gtfs.lib.GtfsDefinitions.RouteType;

/**
 * Definitions and categories for modes found in hafas files (HRDF)
 * Assignment equivalent to GTFS.
 *
 * @author polettif
 */
public final class VehicleTypeDefaults {

	public enum Type {
		//								add		DEFAULT VALUES
		//								to		length	width	accT	egrT	doorOp									capSeat	capSt	pcuEq	usesRN	description
		//								sched.	[m]		[m]		s/pers	s/pers
		AG  ("AG",	RouteType.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Agencytrain"),
		ARZ ("ARZ", RouteType.RAIL,		false,	0.0, 	0.0, 	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Car-carrying train, Autoreisezug"),
		ATZ ("ATZ", RouteType.RAIL,		false,	0.0, 	0.0,	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Car train, Autotunnelzug"),
		BAT	("BAT",	RouteType.FERRY,	true,	50,		6,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	250,	0,		7.1,	false,	"Ship"),
		BAV	("BAV",	RouteType.FERRY,	true,	50,		6,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	250,	0,		7.1,	false,	"Steam ship"),
		BEX ("BEX",	RouteType.RAIL,		true,	150,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	240,	0,		20.4,	false,	"Bernina Express"),
		BUS	("BUS",	RouteType.BUS,		true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Bus"),
		CNL ("CNL",	RouteType.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"CityNightLine"),
		D   ("D",	RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Fast train"),
		EB	("EB",	RouteType.BUS,		true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Eilbus"),
		EC  ("EC",	RouteType.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	700,	0,		27.1,	false,	"EuroCity"),
		EN	("EN",	RouteType.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"EuroNight"),
		EXB	("EXB",	RouteType.BUS,		true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"ExpressBus"),
		EXT	("EXT",	RouteType.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Special train"),
		FAE	("FAE",	RouteType.FERRY,	true,	20,		6,		0.25,	0.25,	VehicleType.DoorOperationMode.serial,	100,	0,		3.1,	false,	"Ferry-boat"),
		FUN	("FUN",	RouteType.FUNICULAR,true,	10,		2.5,	0.5	,	0.5, 	VehicleType.DoorOperationMode.serial,	100,	0,		1.7	,	false,	"Funicular"),
		GB	("GB",	RouteType.GONDOLA,	false,	0.0, 	0.0, 	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Gondelbahn"),
		GEX	("GEX",	RouteType.RAIL,		true,	150,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	210,	0,		20.4,	false,	"Glacier Express"),
		IC	("IC",	RouteType.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	888,	0,		27.1,	false,	"InterCity"),
		ICB	("ICB",	RouteType.BUS,		true,	15,		2.5,	0.0,	0.0,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"intercity bus"),
		ICE	("ICE",	RouteType.RAIL,		true,	205,	3,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	481,	0,		27.7,	false,	"InterCityExpress"),
		ICN	("ICN",	RouteType.RAIL,		true,	360,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	900,	0,		48.4,	false,	"IC-tilting train"),
		IR	("IR",	RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"InterRegio"),
		IRE	("IRE",	RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"InterRegio-Express"),
		KB	("KB",	RouteType.BUS,		true,	12,		2.5,	1,		1,		VehicleType.DoorOperationMode.serial,	30,		0,		2,		true,	"Minibus"),
		LB	("LB",	RouteType.GONDOLA,	true,	6,		3.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	80,		0,		1.2,	false,	"Cableway"),
		M   ("M",	RouteType.SUBWAY,	true,	30,		2.45,	0.1,	0.1,	VehicleType.DoorOperationMode.serial,	300,	0,		4.4,	false,	"Underground"),
		MAT ("M",	RouteType.RAIL,		false,	0.0,	0.0,	0.0,	0.0,	VehicleType.DoorOperationMode.serial,	0,		0,		0.0,	false,	"LeermaterialZ (Reisezugswagen)"),
		MP	("MP", 	RouteType.RAIL,		false,	0.0, 	0.0, 	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"LeermaterialZ Personenbeförd"),
		NB	("NB",	RouteType.BUS,		true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Night-Bus"),
		NFB	("NFB",	RouteType.BUS,		true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Low-floor bus"),
		NFO	("NFO",	RouteType.BUS,		true,	22,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	100,	0,		3.3,	true,	"Low-floor trolley bus"),
		NFT ("NFT",	RouteType.TRAM, 	true,	36,		2.4,	0.25,	0.25,	VehicleType.DoorOperationMode.serial, 	180,	0,		5.2	,	true,	"Low-floor tramway"),
		NZ	("NZ",	RouteType.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Night train"),
		R	("R",	RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Regio"),
		RB	("RB",	RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Regionalbahn"),
		RE	("RE",	RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"RegioExpress"),
		RJ	("RJ",	RouteType.RAIL,		true,	205,	2.9,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.7,	false,	"Railjet"),
		S	("S",	RouteType.RAIL,		true,	300,	2.8,	0.05,	0.05,	VehicleType.DoorOperationMode.serial,	3000,	0,		40.4,	false,	"Urban train"),
		SL	("SL",	RouteType.GONDOLA,	false,	0.0, 	0.0, 	0.0,	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Chairlift, Sesselbahn"),
		SN	("SN",	RouteType.RAIL,		true,	100,	2.9,	0.1,	0.1,	VehicleType.DoorOperationMode.serial,	500,	0,		13.7,	false,	"Night-urban train"),
		T   ("T", 	RouteType.TRAM,		true,	20,		2.2,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	140,	0,		3.1,	true,	"Tramway"),
		TE2 ("TE2", RouteType.RAIL,		true,	360,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	900,	0,		48.4,	false,	"TER200"),
		TER ("TER", RouteType.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Train Express Regional"),
		TGV	("TGV",	RouteType.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Train à grande vit."),
		TRO	("TRO",	RouteType.BUS,		true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"trolley bus"),
		TX	("TX",	RouteType.BUS,      true,	7.5,	1.8,	2,		2,		VehicleType.DoorOperationMode.serial,	4,		0,		1.4,	true,	"Taxi"),
		VAE	("VAE",	RouteType.RAIL,		true,	240,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	620,	0,		32.4,	false,	"Voralpen-Express"),
		ZUG	("ZUG",	RouteType.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Train category unknown"),

		// Gtfs Converter Defaults
		//								            add		DEFAULT VALUES
		//								            to		length	width	accT	egrT	doorOp									capSeat	capSt	pcuEq	usesRN	description
		//								            sched.	[m]		[m]		s/pers	s/pers
		TRAM ("TRAM",       RouteType.TRAM,			true,	36,		2.4,	0.25,	0.25,	VehicleType.DoorOperationMode.serial, 	180,	0,		5.2	,	true,	"tram"),
		SUBWAY ("SUBWAY",   RouteType.SUBWAY,		true,	30,		2.45,	0.1,	0.1,	VehicleType.DoorOperationMode.serial,	300,	0,		4.4,	false,	"subway"),
		RAIL ("RAIL",       RouteType.RAIL,			true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"rail"),
		// BUS exists for HAFAS
		FERRY	("FERRY",	RouteType.FERRY,		true,	50,		6,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	250,	0,		7.1,	false,	"ferry"),
		CABLE_CAR ("CABLE_CAR",RouteType.CABLE_CAR,	true,	12,		3,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	60,		0,		5.2,	false,	"cable car"),
		GONDOLA	("GONDOLA",	RouteType.GONDOLA,		true,	6,		3.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	80,		0,		1.2,	false,	"gondola"),
		FUNICULAR	("FUN",	RouteType.FUNICULAR,	true,	10,		2.5,	0.5	,	0.5, 	VehicleType.DoorOperationMode.serial,	100,	0,		1.7,	false,	"funicular"),
		OTHER	("OTHER",	RouteType.OTHER,		true,	10,		2.5,	0.5	,	0.5, 	VehicleType.DoorOperationMode.serial,	50,	    0,		2.0,    false,	"other");

		public double length, width, accessTime, egressTime, pcuEquivalents;
		public int capacitySeats, capacityStanding;
		public String name, description;
		public VehicleType.DoorOperationMode doorOperation;
		public boolean usesRoadNetwork, addToSchedule;
		public RouteType transportMode;

		Type(String name, RouteType transportMode, boolean addToSchedule, double length, double width, double accessTime, double egressTime, VehicleType.DoorOperationMode doorOperation, int capacitySeats, int capacityStanding, double pcuEquivalents, boolean usesRoadNetwork, String description) {
			this.name = name;
			this.transportMode = transportMode;
			this.addToSchedule = addToSchedule;
			this.length = length;
			this.width = width;
			this.accessTime = accessTime;
			this.egressTime = egressTime;
			this.doorOperation = doorOperation;
			this.capacitySeats = capacitySeats;
			this.capacityStanding = capacityStanding;
			this.pcuEquivalents = pcuEquivalents;
			this.usesRoadNetwork = usesRoadNetwork;
			this.description = description;
		}
	}
}