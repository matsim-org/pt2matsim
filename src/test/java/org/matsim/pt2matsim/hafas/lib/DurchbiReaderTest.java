package org.matsim.pt2matsim.hafas.lib;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for DurchbiReader
 *
 * @author copilot
 */
class DurchbiReaderTest {

	@Test
	void testReadDurchbindungen(@TempDir Path tempDir) throws IOException {
		// Create a temporary DURCHBI file
		Path durchbiFile = tempDir.resolve("DURCHBI");
		String content = """
				*DURCHBI
				000001 000104 8508352 000002 000104 003499
				000003 000104 8508351 000004 000104 003499
				000005 000200 8500001 000006 000200 000001
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		// Read the file
		List<Durchbindung> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(3, durchbindungen.size(), "Should read 3 durchbindungen");
		Assertions.assertEquals("000001", durchbindungen.get(0).firstTripNumber());
		Assertions.assertEquals("000104", durchbindungen.get(0).firstAdministration());
		Assertions.assertEquals("8508352", durchbindungen.get(0).lastStopOfFirstTrip());
		Assertions.assertEquals("000002", durchbindungen.get(0).secondTripNumber());
		Assertions.assertEquals("000104", durchbindungen.get(0).secondAdministration());
		Assertions.assertEquals(3499, durchbindungen.get(0).operationDayBitfeldNumber());

		Assertions.assertEquals("000003", durchbindungen.get(1).firstTripNumber());
		Assertions.assertEquals("000004", durchbindungen.get(1).secondTripNumber());
		Assertions.assertEquals("000005", durchbindungen.get(2).firstTripNumber());
		Assertions.assertEquals("000006", durchbindungen.get(2).secondTripNumber());
	}

	@Test
	void testReadDurchbindungenWithEmptyLines(@TempDir Path tempDir) throws IOException {
		// Create a temporary DURCHBI file with empty lines
		Path durchbiFile = tempDir.resolve("DURCHBI");
		String content = """
				*DURCHBI
				000001 000104 8508352 000002 000104 003499
				
				000003 000104 8508351 000004 000104 003499
				
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		// Read the file
		List<Durchbindung> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(2, durchbindungen.size(), "Should read 2 durchbindungen, skipping empty lines");
		Assertions.assertEquals("000002", durchbindungen.get(0).secondTripNumber());
		Assertions.assertEquals("000004", durchbindungen.get(1).secondTripNumber());
	}

	@Test
	void testReadDurchbindungenMissingFile(@TempDir Path tempDir) throws IOException {
		// Try to read a non-existent file
		Path durchbiFile = tempDir.resolve("NONEXISTENT");

		// Should not throw exception, just return empty list
		List<Durchbindung> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(0, durchbindungen.size(), "Should return empty list for non-existent file");
	}

	@Test
	void testReadDurchbindungenWithComments(@TempDir Path tempDir) throws IOException {
		// Create a temporary DURCHBI file with comments
		Path durchbiFile = tempDir.resolve("DURCHBI");
		String content = """
				*DURCHBI file for testing
				*This is a comment
				000001 000104 8508352 000002 000104 003499
				*Another comment
				000003 000104 8508351 000004 000104 003499
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		// Read the file
		List<Durchbindung> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(2, durchbindungen.size(), "Should read 2 durchbindungen, skipping comments");
		Assertions.assertEquals("000002", durchbindungen.get(0).secondTripNumber());
		Assertions.assertEquals("000004", durchbindungen.get(1).secondTripNumber());
	}

	@Test
	void testReadDurchbindungenSkipsInvalidLines(@TempDir Path tempDir) throws IOException {
		Path durchbiFile = tempDir.resolve("DURCHBI");
		String content = """
				*DURCHBI
				000001 000104 8508352 000002 000104 003499
				000003 000104 8508351 000004 000104 ABCDEF
				000005 000104 8508351
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		List<Durchbindung> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		Assertions.assertEquals(1, durchbindungen.size(), "Only valid fixed-width DURCHBI lines should be parsed");
		Assertions.assertEquals("000001", durchbindungen.get(0).firstTripNumber());
	}
}
