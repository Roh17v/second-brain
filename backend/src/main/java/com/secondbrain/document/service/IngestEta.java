package com.secondbrain.document.service;

/**
 * Remaining embed time from chunk progress and the current ms/chunk average.
 */
public final class IngestEta {

	public static final int EMAIL_THRESHOLD_SECONDS = 60;

	private IngestEta() {
	}

	/**
	 * {@code null} when chunk count is unknown (still reading / OCR).
	 */
	public static Integer remainingSeconds(Integer chunkCount, int embeddedCount, long avgMsPerChunk) {
		if (chunkCount == null || chunkCount <= 0) {
			return null;
		}
		int remaining = Math.max(0, chunkCount - Math.max(0, embeddedCount));
		if (remaining == 0) {
			return 0;
		}
		long msPer = Math.max(1L, avgMsPerChunk);
		return (int) Math.ceil(remaining * (msPer / 1000.0));
	}

	public static boolean shouldNotify(Integer remainingSeconds) {
		return remainingSeconds != null && remainingSeconds > EMAIL_THRESHOLD_SECONDS;
	}

	public static boolean shouldNotify(int chunkCount, long avgMsPerChunk) {
		return shouldNotify(remainingSeconds(chunkCount, 0, avgMsPerChunk));
	}
}
