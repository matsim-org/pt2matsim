package org.matsim.pt2matsim.mapping;

import org.apache.log4j.Logger;

public class Progress {
	private Logger logger = Logger.getLogger(Progress.class);

	private final long total;
	private final String description;

	private long current = 0;
	private long lastOutput = 0;
	
	private Object lock = new Object();

	public Progress(long total, String description) {
		this.total = total;
		this.description = description;
		print();
	}

	public void update(long count) {
		synchronized(lock) {
			current += count;
			print();
		}
	}

	public void update() {
		synchronized(lock) {
			current += 1;
			print();
		}
	}

	private void print() {
		if ((System.nanoTime() - lastOutput >= 1000000000L) || current == total) {
			lastOutput = System.nanoTime();
			logger.info(String.format("%s %d/%d (%.2f%%)", description, current, total, 100.0 * current / total));
		}
	}
}
