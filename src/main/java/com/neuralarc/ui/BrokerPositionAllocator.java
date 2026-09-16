package com.neuralarc.ui;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Splits one broker position across the local strategies that share its symbol.
 *
 * <p>The broker reports a single netted position per symbol, while the app deliberately allows
 * several strategies on one symbol so a position can be worked in parts. Handing the full broker
 * quantity to every one of those rows made a single position look like several: the grid showed the
 * same shares twice and portfolio totals counted them twice.
 *
 * <p>Shares are handed out against what each strategy's own filled orders say it holds, largest
 * claim first and oldest strategy first on a tie, so the allocation is stable between refreshes.
 * Anything the broker holds beyond the local claims goes to a row the grid actually draws - shares
 * found at the broker still belong somewhere, and parking them on a completed or archived row makes
 * the position vanish from the screen while still counting in portfolio totals. A row whose claim
 * cannot be covered simply gets none.
 */
final class BrokerPositionAllocator {
    private BrokerPositionAllocator() {
    }

    /**
     * One strategy's own view of what it holds on a symbol.
     *
     * @param hidden whether the grid never draws this row, so unclaimed shares should not rest here
     */
    record Claim(String strategyId, int localShares, Instant createdAt, boolean hidden) {
        Claim {
            localShares = Math.max(0, localShares);
            createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        }

        Claim(String strategyId, int localShares, Instant createdAt) {
            this(strategyId, localShares, createdAt, false);
        }
    }

    /**
     * @return strategyId - shares of the broker position that row may display. Every claim appears
     *         in the result, including the ones allocated nothing.
     */
    static Map<String, Integer> allocate(int brokerShares, List<Claim> claims) {
        Map<String, Integer> allocation = new LinkedHashMap<>();
        if (claims == null || claims.isEmpty()) {
            return allocation;
        }
        List<Claim> ordered = claims.stream()
                .sorted(Comparator.comparingInt(Claim::localShares).reversed()
                        .thenComparing(Claim::createdAt)
                        .thenComparing(Claim::strategyId))
                .toList();
        for (Claim claim : ordered) {
            allocation.put(claim.strategyId(), 0);
        }
        int remaining = Math.max(0, brokerShares);
        if (remaining == 0) {
            return allocation;
        }
        if (ordered.size() == 1) {
            allocation.put(ordered.getFirst().strategyId(), remaining);
            return allocation;
        }
        for (Claim claim : ordered) {
            if (remaining <= 0) {
                break;
            }
            int allocated = Math.min(remaining, claim.localShares());
            allocation.put(claim.strategyId(), allocated);
            remaining -= allocated;
        }
        if (remaining > 0) {
            // The broker holds more than the local records account for - keep it on a row the grid
            // draws rather than dropping it, so portfolio totals still match the broker and the
            // shares are actually visible. Only when every row is hidden does it fall back to the
            // first, which at least keeps the totals right.
            Claim primary = ordered.stream()
                    .filter(claim -> !claim.hidden())
                    .findFirst()
                    .orElse(ordered.getFirst());
            allocation.put(primary.strategyId(), allocation.get(primary.strategyId()) + remaining);
        }
        return allocation;
    }
}
