package org.matsim.pt2matsim.hafas;

/**
 * Configures the HAFAS input file names used by the converter.
 * Defaults match the previously hard-coded values.
 */
public class HafasFileConfig {

	public static final String DEFAULT_BFKOORD_WGS = "BFKOORD_WGS";
	public static final String DEFAULT_UMSTEIGB = "UMSTEIGB";
	public static final String DEFAULT_METABHF = "METABHF";
	public static final String DEFAULT_STRECKENPT = "STRECKENPT";
	public static final String DEFAULT_KANTEN = "KANTEN";
	public static final String DEFAULT_BETRIEB_DE = "BETRIEB_DE";
	public static final String DEFAULT_FPLAN = "FPLAN";
	public static final String DEFAULT_DURCHBI = "DURCHBI";
	public static final String DEFAULT_BITFELD = "BITFELD";

	private String bfkoordWgs = DEFAULT_BFKOORD_WGS;
	private String umsteigb = DEFAULT_UMSTEIGB;
	private String metabhf = DEFAULT_METABHF;
	private String streckenpt = DEFAULT_STRECKENPT;
	private String kanten = DEFAULT_KANTEN;
	private String betriebDe = DEFAULT_BETRIEB_DE;
	private String fplan = DEFAULT_FPLAN;
	private String durchbi = DEFAULT_DURCHBI;
	private String bitfeld = DEFAULT_BITFELD;

	public String getBfkoordWgs() {
		return bfkoordWgs;
	}

	public HafasFileConfig setBfkoordWgs(String bfkoordWgs) {
		this.bfkoordWgs = bfkoordWgs;
		return this;
	}

	public String getUmsteigb() {
		return umsteigb;
	}

	public HafasFileConfig setUmsteigb(String umsteigb) {
		this.umsteigb = umsteigb;
		return this;
	}

	public String getMetabhf() {
		return metabhf;
	}

	public HafasFileConfig setMetabhf(String metabhf) {
		this.metabhf = metabhf;
		return this;
	}

	public String getStreckenpt() {
		return streckenpt;
	}

	public HafasFileConfig setStreckenpt(String streckenpt) {
		this.streckenpt = streckenpt;
		return this;
	}

	public String getKanten() {
		return kanten;
	}

	public HafasFileConfig setKanten(String kanten) {
		this.kanten = kanten;
		return this;
	}

	public String getBetriebDe() {
		return betriebDe;
	}

	public HafasFileConfig setBetriebDe(String betriebDe) {
		this.betriebDe = betriebDe;
		return this;
	}

	public String getFplan() {
		return fplan;
	}

	public HafasFileConfig setFplan(String fplan) {
		this.fplan = fplan;
		return this;
	}

	public String getDurchbi() {
		return durchbi;
	}

	public HafasFileConfig setDurchbi(String durchbi) {
		this.durchbi = durchbi;
		return this;
	}

	public String getBitfeld() {
		return bitfeld;
	}

	public HafasFileConfig setBitfeld(String bitfeld) {
		this.bitfeld = bitfeld;
		return this;
	}
}
