package org.matsim.pt2matsim.hafas.lib;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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
				000001 000002
				000003 000004
				000005 000006
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		// Read the file
		Map<String, String> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(3, durchbindungen.size(), "Should read 3 durchbindungen");
		Assertions.assertEquals("000002", durchbindungen.get("000001"), "Trip 000001 should continue as 000002");
		Assertions.assertEquals("000004", durchbindungen.get("000003"), "Trip 000003 should continue as 000004");
		Assertions.assertEquals("000006", durchbindungen.get("000005"), "Trip 000005 should continue as 000006");
	}

	@Test
	void testReadDurchbindungenWithEmptyLines(@TempDir Path tempDir) throws IOException {
		// Create a temporary DURCHBI file with empty lines
		Path durchbiFile = tempDir.resolve("DURCHBI");
		String content = """
				*DURCHBI
				000001 000002
				
				000003 000004
				
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		// Read the file
		Map<String, String> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(2, durchbindungen.size(), "Should read 2 durchbindungen, skipping empty lines");
		Assertions.assertEquals("000002", durchbindungen.get("000001"));
		Assertions.assertEquals("000004", durchbindungen.get("000003"));
	}

	@Test
	void testReadDurchbindungenMissingFile(@TempDir Path tempDir) throws IOException {
		// Try to read a non-existent file
		Path durchbiFile = tempDir.resolve("NONEXISTENT");

		// Should not throw exception, just return empty map
		Map<String, String> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(0, durchbindungen.size(), "Should return empty map for non-existent file");
	}

	@Test
	void testReadDurchbindungenWithComments(@TempDir Path tempDir) throws IOException {
		// Create a temporary DURCHBI file with comments
		Path durchbiFile = tempDir.resolve("DURCHBI");
		String content = """
				*DURCHBI file for testing
				*This is a comment
				000001 000002
				*Another comment
				000003 000004
				""";
		Files.writeString(durchbiFile, content, StandardCharsets.ISO_8859_1);

		// Read the file
		Map<String, String> durchbindungen = DurchbiReader.readDurchbindungen(durchbiFile.toString(), StandardCharsets.ISO_8859_1);

		// Verify results
		Assertions.assertEquals(2, durchbindungen.size(), "Should read 2 durchbindungen, skipping comments");
		Assertions.assertEquals("000002", durchbindungen.get("000001"));
		Assertions.assertEquals("000004", durchbindungen.get("000003"));
	}
}
