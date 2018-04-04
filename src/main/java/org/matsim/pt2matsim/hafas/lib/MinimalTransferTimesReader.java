package org.matsim.pt2matsim.hafas.lib;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.*;

/**
 * reads minimal transfer times between transit stop facilities from UMSTEIGB and METABHF to schedule
 * minimal transfer transfer times from UMSTEIGV, UMSTEIGL and UMSTEIGZ are not read (there are no data structures in MATSim for it).
 *
 * @author jlieberherr
 */
public class MinimalTransferTimesReader {

    protected static Logger log = Logger.getLogger(MinimalTransferTimesReader.class);

    public static void run(TransitSchedule schedule, String pathToHafasFolder, String UMSTEIGB, String METABHF) throws IOException {

        MinimalTransferTimes minimalTransferTimes = schedule.getMinimalTransferTimes();

        // read from UMSTEIGB
        if (new File(pathToHafasFolder, UMSTEIGB).exists()) {
            BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(pathToHafasFolder + UMSTEIGB), "utf-8"));
            String newLine = readsLines.readLine();
            while (newLine != null) {
                /*
                1-7 INT32 Die Nummer der Haltestelle.
                9-10 INT16 Umsteigezeit IC-IC
                12-13 INT16 Umsteigezeit zwischen allen anderen Gattungskombinationen
                15ff CHAR (optional) Klartext des Haltestellennamens Nur zur besseren Lesbarkeit
				 */
                Id<TransitStopFacility> stopId = Id.create(newLine.substring(0, 7), TransitStopFacility.class);
                double transferTime = Integer.valueOf(newLine.substring(11, 13)) * 60;
                minimalTransferTimes.set(stopId, stopId, transferTime);
                newLine = readsLines.readLine();
            }
            readsLines.close();
        } else {
            log.info("   UMSTEIGB does not exist!");
        }

        // read from METABHF
        if (new File(pathToHafasFolder, METABHF).exists()) {
            BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(pathToHafasFolder + METABHF), "utf-8"));
            String newLine = readsLines.readLine();
            while (newLine != null) {
                /*
                1-7 INT32 Haltestellennummer 1.
                9-15 INT32 Haltestellennummer 2.
                17-19 INT16 Dauer des Übergangs in Minuten.
                20-20 CHAR (optional) „S“ als Trennzeichen für den
                Sekundenaufschlag zur Fusswegdauer
                21-22 INT16 (optional) Sekundenaufschlag zur Fusswegdauer
                Sekunden werden von INFO+ zu Minuten aufgerundet.
				 */

                if (!newLine.startsWith("*") && !newLine.substring(7, 8).equals(":"))  {
                    Id<TransitStopFacility> fromStopId = Id.create(newLine.substring(0, 7), TransitStopFacility.class);
                    Id<TransitStopFacility> toStopId = Id.create(newLine.substring(8, 15), TransitStopFacility.class);
                    double transferTime = Integer.valueOf(newLine.substring(16, 19)) * 60;
                    minimalTransferTimes.set(fromStopId, toStopId, transferTime);
                }
                newLine = readsLines.readLine();
            }
            readsLines.close();
        } else {
            log.info("   METABHF does not exist!");
        }
    }

}
