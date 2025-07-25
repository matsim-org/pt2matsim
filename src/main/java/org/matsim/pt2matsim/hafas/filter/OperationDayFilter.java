package org.matsim.pt2matsim.hafas.filter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.pt2matsim.hafas.HafasConverter;
import org.matsim.pt2matsim.hafas.lib.BitfeldAnalyzer;
import org.matsim.pt2matsim.hafas.lib.ECKDATENReader;
import org.matsim.pt2matsim.hafas.lib.FPLANRoute;

public class OperationDayFilter extends AbstractFilter implements HafasFilter {

    private final Set<Integer> bitfeldNummern;
    private final Logger log = LogManager.getLogger(HafasConverter.class);

    public OperationDayFilter(String chosenDateString, String hafasFolder, Charset encodingCharset) throws IOException {
        if(!hafasFolder.endsWith("/")) hafasFolder += "/";
        int dayNr = getDayNumber(chosenDateString, hafasFolder, encodingCharset);
        this.bitfeldNummern = getBitfeldNummers(dayNr, hafasFolder, encodingCharset);
    }

    public OperationDayFilter(String hafasFolder, Charset encodingCharset) throws IOException {
        if(!hafasFolder.endsWith("/")) hafasFolder += "/";
        this.bitfeldNummern = getBitfeldNummers(-1, hafasFolder, encodingCharset);
    }

    @Override
    public boolean keepRoute(FPLANRoute route) {
        List<String> localBitfeldNrStr = route.getLocalBitfeldNumbers().stream().map(String::valueOf).toList();
        boolean keep = route.getLocalBitfeldNumbers().stream().anyMatch(this.bitfeldNummern::contains);
        if (keep) this.getIdsFilteredIn().addAll(localBitfeldNrStr);
        else this.getIdsFilteredOut().addAll(localBitfeldNrStr);
        return keep;
    }

    private int getDayNumber (String chosenDateString, String hafasFolder, Charset encodingCharset) throws IOException {
        LocalDate fahrplanStartDate = ECKDATENReader.getFahrPlanStart(hafasFolder, encodingCharset);
        LocalDate fahrplanEndDate = ECKDATENReader.getFahrPlanEnd(hafasFolder, encodingCharset);
        try {
            LocalDate chosenDate = ECKDATENReader.getDate(chosenDateString);

            if (chosenDate.isBefore(fahrplanStartDate) || chosenDate.isAfter(fahrplanEndDate)) {
                throw new IllegalArgumentException(
                    String.format("Chosen date %s is outside fahrplan period: (%s, %s)", chosenDate, fahrplanStartDate, fahrplanEndDate)
                );
            }

            return (int) ChronoUnit.DAYS.between(fahrplanStartDate, chosenDate);

        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                "Format of chosen date (should be dd.MM.yyyy) is invalid: " + chosenDateString
            );
        }
    }

    private Set<Integer> getBitfeldNummers(int dayNr, String hafasFolder, Charset encodingCharset) throws IOException {
        // 3. Read all ids for work-day-routes from HAFAS-BITFELD
        log.info("  Read bitfeld numbers...");
        Set<Integer> bitfeldNummern = new HashSet<>();
        if (dayNr < 0) {
            bitfeldNummern = BitfeldAnalyzer.findBitfeldnumbersOfBusiestDay(hafasFolder + "FPLAN", hafasFolder + "BITFELD");
            log.info("      nb of bitfields at busiest day: " + bitfeldNummern.size());
        } else {
            // TODO: check if dayNr is within the timetable period defined in ECKDATEN
            bitfeldNummern = BitfeldAnalyzer.getBitfieldsAtValidDay(dayNr, hafasFolder, encodingCharset);
            log.info("      nb of bitfields valid at day " + dayNr + ": " + bitfeldNummern.size());
        }
        log.info("  Read bitfeld numbers... done.");

        return bitfeldNummern;
    }

    public Set<Integer> getBitfeldNummern() {
        return bitfeldNummern;
    }
}
