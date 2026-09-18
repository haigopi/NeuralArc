package com.neuralarc.ui;

import java.util.function.IntSupplier;

/**
 * Slot-machine style frames for a figure that just changed: every digit spins, then the reels stop
 * one by one from left to right until the real value shows. Everything that is not a digit — "$",
 * ",", ".", "%", signs and words — stays put, so the shape of the number is readable from the start.
 */
final class RollingDigits {
    private RollingDigits() {
    }

    /**
     * The text to show at {@code progress} (0 = just started, 1 = settled). Digit {@code k} of
     * {@code n} locks once progress passes {@code (k + 1) / (n + 1)}; unlocked digits come from
     * {@code randomDigit}.
     */
    static String frame(String target, double progress, IntSupplier randomDigit) {
        if (target == null || progress >= 1) {
            return target;
        }
        int digitCount = 0;
        for (int i = 0; i < target.length(); i++) {
            if (Character.isDigit(target.charAt(i))) {
                digitCount++;
            }
        }
        StringBuilder frame = new StringBuilder(target.length());
        int digitIndex = 0;
        for (int i = 0; i < target.length(); i++) {
            char c = target.charAt(i);
            if (!Character.isDigit(c)) {
                frame.append(c);
                continue;
            }
            boolean locked = progress >= (digitIndex + 1) / (double) (digitCount + 1);
            frame.append(locked ? c : (char) ('0' + Math.floorMod(randomDigit.getAsInt(), 10)));
            digitIndex++;
        }
        return frame.toString();
    }
}
