public class Utils {
    private static double ERTT = 0;
    private static double EDEV = 0;
    private static final double ALPHA = 0.875;
    private static final double BETA = 0.75;

    public static long initialTimeout() {
        return 5_000_000_000L; // 5 seconds in nanoseconds
    }

    public static long updateTimeout(long sentTime, long ackTime, boolean isFirstAck) {
        long sampleRTT = ackTime - sentTime;
        if (isFirstAck) {
            ERTT = sampleRTT;
            EDEV = 0;
        } else {
            double SRTT = sampleRTT;
            double SDEV = Math.abs(SRTT - ERTT);
            ERTT = ALPHA * ERTT + (1 - ALPHA) * SRTT;
            EDEV = BETA * EDEV + (1 - BETA) * SDEV;
        }
        return (long) (ERTT + 4 * EDEV);
    }

    public static double now() {
        return System.nanoTime() / 1e9;
    }
}
