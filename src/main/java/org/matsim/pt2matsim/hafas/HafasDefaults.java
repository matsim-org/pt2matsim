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


package org.matsim.pt2matsim.hafas;

import org.matsim.vehicles.VehicleType;

/**
 * Definitions and categories for modes found in hafas files (HRDF)
 * Assignment equivalent to GTFS.
 *
 * @author polettif
 */
public final class HafasDefaults {

	public enum TransportModes {

		/** 0 - Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area. */
		TRAM("tram"),

		/** 1 - Subway, Metro. Any underground rail system within a metropolitan area. */
		SUBWAY("subway"),

		/** 2 - Rail. Used for intercity or long-distance travel. */
		RAIL("rail"),

		/** 3 - Bus. Used for short- and long-distance bus routes. */
		BUS("bus"),

		/** 4 - Ferry. Used for short- and long-distance boat service. */
		FERRY("ferry"),

		/** 5 - Cable car. Used for street-level cable cars where the cable runs beneath the car. */
		CABLE_CAR("cablecar"),

		/** 6 - Gondola, Suspended cable car. Typically used for aerial cable cars where the car is suspended from the cable. */
		GONDOLA("gondola"),

		/** 7 - Funicular. Any rail system designed for steep inclines. */
		FUNICULAR("funicular"),

		NONE("");

		public String modeName;

		TransportModes(String modeName) {
			this.modeName = modeName;
		}
	}

	public enum Vehicles {
		//										add		DEFAULT VALUES
		//										to		length	width	accT	egrT	doorOp									capSeat	capSt	pcuEq	usesRN	description
		//										sched.	[m]		[m]		s/pers	s/pers
		AG  ("AG",	TransportModes.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Agencytrain"),
		ARZ ("ARZ", TransportModes.NONE,		false,	0.0, 	0.0, 	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Car-carrying train, Autoreisezug"),
		ATZ ("ATZ", TransportModes.NONE,		false,	0.0, 	0.0,	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Car train, Autotunnelzug"),
		BAT	("BAT",	TransportModes.FERRY,		true,	50,		6,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	250,	0,		7.1,	false,	"Ship"),
		BAV	("BAV",	TransportModes.FERRY,		true,	50,		6,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	250,	0,		7.1,	false,	"Steam ship"),
		BEX ("BEX",	TransportModes.RAIL,		true,	150,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	240,	0,		20.4,	false,	"Bernina Express"),
		BUS	("BUS",	TransportModes.BUS,			true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Bus"),
		CNL ("CNL",	TransportModes.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"CityNightLine"),
		D   ("D",	TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Fast train"),
		EB	("EB",	TransportModes.BUS,			true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Eilbus"),
		EC  ("EC",	TransportModes.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	700,	0,		27.1,	false,	"EuroCity"),
		EN	("EN",	TransportModes.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"EuroNight"),
		EXB	("EXB",	TransportModes.BUS,			true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"ExpressBus"),
		EXT	("EXT",	TransportModes.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Special train"),
		FAE	("FAE",	TransportModes.FERRY,		true,	20,		6,		0.25,	0.25,	VehicleType.DoorOperationMode.serial,	100,	0,		3.1,	false,	"Ferry-boat"),
		FUN	("FUN",	TransportModes.FUNICULAR,	true,	10,		2.5,	0.5	,	0.5, 	VehicleType.DoorOperationMode.serial,	100,	0,		1.7	,	false,	"Funicular"),
		GB	("GB",	TransportModes.GONDOLA,		false,	0.0, 	0.0, 	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Gondelbahn"),
		GEX	("GEX",	TransportModes.RAIL,		true,	150,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	210,	0,		20.4,	false,	"Glacier Express"),
		IC	("IC",	TransportModes.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	888,	0,		27.1,	false,	"InterCity"),
		ICB	("ICB",	TransportModes.BUS,			true,	15,		2.5,	0.0,	0.0,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"intercity bus"),
		ICE	("ICE",	TransportModes.RAIL,		true,	205,	3,		0.5,	0.5,	VehicleType.DoorOperationMode.serial,	481,	0,		27.7,	false,	"InterCityExpress"),
		ICN	("ICN",	TransportModes.RAIL,		true,	360,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	900,	0,		48.4,	false,	"IC-tilting train"),
		IR	("IR",	TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"InterRegio"),
		IRE	("IRE",	TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"InterRegio-Express"),
		KB	("KB",	TransportModes.BUS,			true,	12,		2.5,	1,		1,		VehicleType.DoorOperationMode.serial,	30,		0,		2,		true,	"Minibus"),
		LB	("LB",	TransportModes.GONDOLA,		true,	6,		3.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	80,		0,		1.2,	false,	"Cableway"),
		M   ("M",	TransportModes.SUBWAY,		true,	30,		2.45,	0.1,	0.1,	VehicleType.DoorOperationMode.serial,	300,	0,		4.4,	false,	"Underground"),
		MAT ("M",	TransportModes.NONE,		false,	0.0,	0.0,	0.0,	0.0,	VehicleType.DoorOperationMode.serial,	0,		0,		0.0,	false,	"LeermaterialZ (Reisezugswagen)"),
		MP	("MP", 	TransportModes.NONE,		false,	0.0, 	0.0, 	0.0, 	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"LeermaterialZ Personenbeförd"),
		NB	("NB",	TransportModes.BUS,			true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Night-Bus"),
		NFB	("NFB",	TransportModes.BUS,			true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"Low-floor bus"),
		NFO	("NFO",	TransportModes.BUS,			true,	22,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	100,	0,		3.3,	true,	"Low-floor trolley bus"),
		NFT ("NFT",	TransportModes.TRAM, 		true,	36,		2.4,	0.25,	0.25,	VehicleType.DoorOperationMode.serial, 	180,	0,		5.2	,	true,	"Low-floor tramway"),
		NZ	("NZ",	TransportModes.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Night train"),
		R	("R",	TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Regio"),
		RB	("RB",	TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Regionalbahn"),
		RE	("RE",	TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"RegioExpress"),
		RJ	("RJ",	TransportModes.RAIL,		true,	205,	2.9,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.7,	false,	"Railjet"),
		S	("S",	TransportModes.RAIL,		true,	300,	2.8,	0.05,	0.05,	VehicleType.DoorOperationMode.serial,	3000,	0,		40.4,	false,	"Urban train"),
		SL	("SL",	TransportModes.NONE,		false,	0.0, 	0.0, 	0.0,	0.0, 	VehicleType.DoorOperationMode.serial, 	0, 		0, 		0.0, 	false, 	"Chairlift, Sesselbahn"),
		SN	("SN",	TransportModes.RAIL,		true,	100,	2.9,	0.1,	0.1,	VehicleType.DoorOperationMode.serial,	500,	0,		13.7,	false,	"Night-urban train"),
		T   ("T", 	TransportModes.TRAM,		true,	20,		2.2,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	140,	0,		3.1,	true,	"Tramway"),
		TE2 ("TE2", TransportModes.RAIL,		true,	360,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	900,	0,		48.4,	false,	"TER200"),
		TER ("TER", TransportModes.RAIL,		true,	150,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		20.4,	false,	"Train Express Regional"),
		TGV	("TGV",	TransportModes.RAIL,		true,	200,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Train à grande vit."),
		TRO	("TRO",	TransportModes.BUS,			true,	18,		2.5,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	70,		0,		2.8,	true,	"trolley bus"),
		TX	("TX",	TransportModes.BUS,			true,	7.5,	1.8,	2,		2,		VehicleType.DoorOperationMode.serial,	4,		0,		1.4,	true,	"Taxi"),
		VAE	("VAE",	TransportModes.RAIL,		true,	240,	2.8,	0.5,	0.5,	VehicleType.DoorOperationMode.serial,	620,	0,		32.4,	false,	"Voralpen-Express"),
		ZUG	("ZUG",	TransportModes.RAIL,		true,	200,	2.8,	0.25,	0.25,	VehicleType.DoorOperationMode.serial,	400,	0,		27.1,	false,	"Train category unknown");

		public double length, width, accessTime, egressTime, pcuEquivalents;
		public int capacitySeats, capacityStanding;
		public String name, description;
		public VehicleType.DoorOperationMode doorOperation;
		public boolean usesRoadNetwork, addToSchedule;
		public TransportModes transportMode;

		Vehicles(String name, TransportModes transportMode, boolean addToSchedule, double length, double width, double accessTime, double egressTime, VehicleType.DoorOperationMode doorOperation, int capacitySeats, int capacityStanding, double pcuEquivalents, boolean usesRoadNetwork, String description) {
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
			this.capacityStanding = capacityStanding;
			this.pcuEquivalents = pcuEquivalents;
			this.usesRoadNetwork = usesRoadNetwork;
			this.description = description;
		}
	}
}