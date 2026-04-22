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
              .append(c.stopActivationPrice().toPlainString()).append(",")
              .append(c.sellTriggerPrice().toPlainString()).append(",")
              .append(c.lossBuyLevel1Price().toPlainString()).append(",")
              .append(c.lossBuyLevel1Qty()).append(",")
              .append(c.lossBuyLevel2Price().toPlainString()).append(",")
              .append(c.lossBuyLevel2Qty()).append(",")
              .append(c.pollingSeconds()).append(",")
              .append(c.paperTrading()).append(",")
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
                StrategyConfig config = new StrategyConfig(
                        p[0],
                        new BigDecimal(p[1]),
                        Integer.parseInt(p[2]),
                        new BigDecimal(p[3]),
                        new BigDecimal(p[4]),
                        new BigDecimal(p[5]),
                        Integer.parseInt(p[6]),
                        new BigDecimal(p[7]),
                        Integer.parseInt(p[8]),
                        Integer.parseInt(p[9]),
                        Boolean.parseBoolean(p[10])
                );
                boolean paused = Boolean.parseBoolean(p[11]);
                result.add(new StrategyEntry(config, paused));
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return result;
    }

    public record StrategyEntry(StrategyConfig config, boolean paused) {}
}
