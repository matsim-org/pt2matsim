package org.matsim.pt2matsim.hafas.lib;

import org.apache.log4j.Logger;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ECKDATENReader {

    protected static Logger log = Logger.getLogger(ECKDATENReader.class);
    private static final String ECKDATEN = "ECKDATEN";
    /*
        1 1−10 CHAR Fahrplanstart im Format TT.MM.JJJJ
        2 1−10 CHAR Fahrplanende im Format TT.MM.JJJJ
        3 1ff CHAR Fahrplanbezeichnung
     */
    public static LocalDate getFahrPlanStart(String pathToHafasFolder) throws IOException {
        if (new File(pathToHafasFolder, ECKDATEN).exists()) {
            BufferedReader readsLines = new BufferedReader(new InputStreamReader(new FileInputStream(pathToHafasFolder + ECKDATEN), "utf-8"));
            LocalDate startDate = getDate(readsLines.readLine());
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
            readsLines.readLine();
            LocalDate endDate = getDate(readsLines.readLine());

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
