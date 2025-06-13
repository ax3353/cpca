package org.zk.cpca.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 简称词典，动态加载外部CSV配置
 *
 * @author zk
 */
public class ShortNameDict {

    private static final String CSV_RESOURCE_PATH = "/short_name_2025.csv";

    private static final Map<String, String> SHORT_TO_FULL;

    static {
        SHORT_TO_FULL = loadFromCsv();
    }

    private static Map<String, String> loadFromCsv() {
        Map<String, String> map = new HashMap<>();

        try (InputStream is = ShortNameDict.class.getResourceAsStream(CSV_RESOURCE_PATH)) {
            if (is == null) {
                throw new RuntimeException("Cannot find " + CSV_RESOURCE_PATH + " in classpath");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue; // 跳过空行和注释行
                    }

                    // 逗号分隔，第一列全称，第二列简称
                    String[] parts = line.split(",");
                    if (parts.length != 2) {
                        continue; // 跳过格式异常行
                    }

                    String fullName = parts[0].trim();
                    String abbr = parts[1].trim();

                    // map的key用简称，value用全称，方便简称->全称映射
                    map.put(abbr, fullName);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading short names CSV file", e);
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * 根据简称或全称获取全称
     */
    public static String toFullName(String shortOrFull) {
        return SHORT_TO_FULL.getOrDefault(shortOrFull, shortOrFull);
    }

    public static Map<String, String> getShortNameMap() {
        return SHORT_TO_FULL;
    }
}

