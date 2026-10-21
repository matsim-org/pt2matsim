package org.matsim.pt2matsim.hafas.lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

public class StreckenptReader {
    private static final Logger log = LogManager.getLogger(StreckenptReader.class);

    public static Map<String, Coord> readStreckenpt(String filePath, CoordinateTransformation transformation, Charset encodingCharset) throws IOException {
        Map<String, Coord> streckenpunkte = new HashMap<>();
        File file = new File(filePath);


        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encodingCharset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("*")) continue;

                String[] parts = line.split(" +");
                if (parts.length >= 3) {
                    String id = parts[0];
                    double lon = Double.parseDouble(parts[1]);
                    double lat = Double.parseDouble(parts[2]);
                    Coord coord = new Coord(lon, lat);
                    if (transformation != null) {
                        coord = transformation.transform(coord);
                    }
                    streckenpunkte.put(id, coord);
                }
            }
        }
        log.info("Read {} streckenpunkte.", streckenpunkte.size());
        return streckenpunkte;
    }
}
