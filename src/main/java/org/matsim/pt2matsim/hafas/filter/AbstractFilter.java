package org.matsim.pt2matsim.hafas.filter;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;

public abstract class AbstractFilter implements HafasFilter {

    private final Set<String> idsFilteredIn = new TreeSet<>();
    private final Set<String> idsFilteredOut = new TreeSet<>();

    public Set<String> getIdsFilteredIn() {
        return this.idsFilteredIn;
    }

    public Set<String> getIdsFilteredOut() {
        return this.idsFilteredOut;
    }

    @Override
    public void writeFilterStats(String outputDir) {
        String filterName = this.getClass().getSimpleName();
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Paths.get(outputDir, filterName + "_filteredIn.txt").toString()), "utf-8"))) {
            for (String id : getIdsFilteredIn()) {
                bw.write(id + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(Paths.get(outputDir, filterName + "_filteredOut.txt").toString()), "utf-8"))) {
            for (String id : getIdsFilteredOut()) {
                bw.write(id + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
