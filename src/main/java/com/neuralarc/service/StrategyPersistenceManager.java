package com.neuralarc.service;

import com.neuralarc.model.StrategyConfig;
import com.neuralarc.security.EncryptionUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists strategy configs and their paused state to an AES-GCM encrypted file.
 * Each strategy is one comma-delimited line; the whole content is encrypted as a Base64 blob.
 */
public class StrategyPersistenceManager {

    private static final String LINE_SEP = "\n";

    private final Path filePath;
    private final String passphrase;
    private final EncryptionUtil encryptionUtil = new EncryptionUtil();

    public StrategyPersistenceManager(Path filePath, String passphrase) {
        this.filePath = filePath;
        this.passphrase = passphrase;
    }

    public void save(List<StrategyEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (StrategyEntry entry : entries) {
            StrategyConfig c = entry.config();
            sb.append(c.symbol()).append(",")
              .append(c.baseBuyPrice().toPlainString()).append(",")
              .append(c.baseBuyQty()).append(",")
              .append(c.stopLossEnabled()).append(",")
              .append(c.stopLoss().toPlainString()).append(",")
              .append(c.sellTriggerPrice().toPlainString()).append(",")
              .append(c.lossBuyLevel1Price().toPlainString()).append(",")
              .append(c.lossBuyLevel1Qty()).append(",")
              .append(c.lossBuyLevel2Price().toPlainString()).append(",")
              .append(c.lossBuyLevel2Qty()).append(",")
              .append(c.pollingSeconds()).append(",")
              .append(c.paperTrading()).append(",")
              .append(c.profitHoldEnabled()).append(",")
              .append(c.profitHoldType().name()).append(",")
              .append(c.profitHoldPercent().toPlainString()).append(",")
              .append(c.profitHoldAmount().toPlainString()).append(",")
              .append(c.repeatCycleAfterProfitExitEnabled()).append(",")
              .append(entry.paused())
              .append(LINE_SEP);
        }
        try {
            Files.createDirectories(filePath.getParent());
            String encrypted = encryptionUtil.encrypt(sb.toString(), passphrase);
            Files.writeString(filePath, encrypted);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save strategies", e);
        }
    }

    public List<StrategyEntry> load() {
        List<StrategyEntry> result = new ArrayList<>();
        if (!Files.exists(filePath)) {
            return result;
        }
        try {
            String encrypted = Files.readString(filePath).trim();
            if (encrypted.isBlank()) {
                return result;
            }
            String plain = encryptionUtil.decrypt(encrypted, passphrase);
            for (String line : plain.split(LINE_SEP)) {
                line = line.trim();
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 12) {
                    continue;
                }
                boolean legacyFormat = p.length < 16;
                boolean hasStopLossEnabledFlag = p.length >= 17;
                boolean stopLossEnabled = hasStopLossEnabledFlag ? Boolean.parseBoolean(p[3]) : true;
                int offset = hasStopLossEnabledFlag ? 1 : 0;
                boolean profitHoldEnabled = Boolean.parseBoolean(p[11 + offset]);
                com.neuralarc.model.ProfitHoldType profitHoldType = legacyFormat
                        ? com.neuralarc.model.ProfitHoldType.PERCENT_TRAILING
                        : com.neuralarc.model.ProfitHoldType.valueOf(p[12 + offset]);
                BigDecimal profitHoldPercent = legacyFormat
                        ? (profitHoldEnabled ? new BigDecimal("10.00") : BigDecimal.ZERO)
                        : new BigDecimal(p[13 + offset]);
                BigDecimal profitHoldAmount = legacyFormat
                        ? BigDecimal.ZERO
                        : new BigDecimal(p[14 + offset]);
                boolean repeatCycleAfterProfitExitEnabled = p.length > 16 + offset
                        && Boolean.parseBoolean(p[15 + offset]);
                int pausedIndex = legacyFormat ? 12 : (repeatCycleAfterProfitExitEnabled ? 16 + offset : 15 + offset);
                StrategyConfig config = new StrategyConfig(
                        p[0],
                        new BigDecimal(p[1]),
                        Integer.parseInt(p[2]),
                        stopLossEnabled,
                        new BigDecimal(p[3 + offset]),
                        new BigDecimal(p[4 + offset]),
                        new BigDecimal(p[5 + offset]),
                        Integer.parseInt(p[6 + offset]),
                        new BigDecimal(p[7 + offset]),
                        Integer.parseInt(p[8 + offset]),
                        false,
                        BigDecimal.ZERO,
                        Integer.parseInt(p[9 + offset]),
                        Boolean.parseBoolean(p[10 + offset]),
                        profitHoldEnabled,
                        profitHoldType,
                        profitHoldPercent,
                        profitHoldAmount,
                        repeatCycleAfterProfitExitEnabled
                );
                boolean paused = Boolean.parseBoolean(p[pausedIndex]);
                result.add(new StrategyEntry(config, paused));
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return result;
    }

    public record StrategyEntry(StrategyConfig config, boolean paused) {}
}
