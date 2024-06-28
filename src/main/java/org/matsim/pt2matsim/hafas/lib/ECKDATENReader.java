package org.matsim.pt2matsim.hafas.lib;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ECKDATENReader {

    protected static Logger log = LogManager.getLogger(ECKDATENReader.class);
    private static final String ECKDATEN = "ECKDATEN";
    /*
        1 1−10 CHAR Fahrplanstart im Format TT.MM.JJJJ
        2 1−10 CHAR Fahrplanende im Format TT.MM.JJJJ
        3 1ff CHAR Fahrplanbezeichnung
     */
    public static LocalDate getFahrPlanStart(String pathToHafasFolder) throws IOException {
        if (new File(pathToHafasFolder, ECKDATEN).exists()) {
            BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(pathToHafasFolder + ECKDATEN), "utf-8"));
            String line;
            String firstLineAfterComments = null;

            while ((line = readsLines.readLine()) != null) {
                if (line.startsWith("*")) {
                    continue;
                }
                firstLineAfterComments = line;
                break;
            }
            LocalDate startDate = getDate(firstLineAfterComments);
            readsLines.close();
            return startDate;
        } else {
            log.error("    ECKDATEN does not exist!");
            return null;
        }
    }

    public static LocalDate getFahrPlanEnd(String pathToHafasFolder) throws IOException {
        if (new File(pathToHafasFolder, ECKDATEN).exists()) {
            BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(pathToHafasFolder + ECKDATEN), "utf-8"));
            String line;
            String secondLineAfterComments = null;

            while ((line = readsLines.readLine()) != null) {
                if (line.startsWith("*")) {
                    continue;
                }
                secondLineAfterComments = readsLines.readLine();
                break;
            }
            LocalDate endDate = getDate(secondLineAfterComments);

            readsLines.close();
            return endDate;
        } else {
            log.error("    ECKDATEN does not exist!");
            return null;
        }
    }

    public static LocalDate getDate(String s) throws DateTimeParseException {
        return LocalDate.parse(s, DateTimeFormatter.ofPattern("d.MM.yyyy"));
    }

}
