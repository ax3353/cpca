package org.zk.cpca.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.zk.cpca.model.AddressTrace;
import org.zk.cpca.model.ParseResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 省市区解析，支持加载外部json格式数据
 *
 * @author zk
 */
@Slf4j
public class AddressParser {
    private static final String DEFAULT_JSON_FILE = "cpca_2025.json";
    private final AddressIndex addressIndex;
    private final AhoCorasickAutomaton automaton;

    /**
     * 使用默认的资源文件初始化
     */
    public AddressParser() {
        this(DEFAULT_JSON_FILE);
    }

    /**
     * 使用指定的资源文件初始化
     *
     * @param resourcePath resources目录下的文件路径
     */
    public AddressParser(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }

            String jsonData = readInputStream(inputStream);

            AddressDataValidator.validateJsonFormat(jsonData);

            this.addressIndex = new AddressIndex(jsonData);
            this.automaton = buildAutomaton();
        } catch (IOException e) {
            log.error("Failed to read JSON file from resources: {}", resourcePath, e);
            throw new RuntimeException("Failed to initialize AddressParser", e);
        }
    }

    /**
     * 使用外部文件路径初始化
     */
    public AddressParser(Path jsonFilePath) {
        try {
            String jsonData = new String(Files.readAllBytes(jsonFilePath), StandardCharsets.UTF_8);

            this.addressIndex = new AddressIndex(jsonData);
            this.automaton = buildAutomaton();
        } catch (IOException e) {
            log.error("Failed to read JSON file from path: {}", jsonFilePath, e);
            throw new RuntimeException("Failed to initialize AddressParser", e);
        }
    }

    /**
     * 将InputStream转换为String的辅助方法
     */
    private String readInputStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    private AhoCorasickAutomaton buildAutomaton() {
        AhoCorasickAutomaton automaton = new AhoCorasickAutomaton();

        if (addressIndex == null || addressIndex.getAllKeywords() == null) {
            throw new IllegalStateException("Address index or keywords are null");
        }

        for (String keyword : addressIndex.getAllKeywords()) {
            if (keyword != null && !keyword.isEmpty()) {
                automaton.addPattern(keyword);
            }
        }

        automaton.buildFailurePointers();
        return automaton;
    }

    public List<ParseResult> parse(String address) {
        return parse(address, null);
    }

    public List<ParseResult> parse(String address, FilterCondition filter) {
        if (StringUtils.isBlank(address)) {
            return Collections.emptyList();
        }

        address = AddressSupport.toFullName(address);

        try {
            List<AhoCorasickAutomaton.MatchResult> matches = automaton.findAll(address);
            if (matches == null || matches.isEmpty()) {
                return Collections.emptyList();
            }

            // 获取所有可能的地址组合
            List<ParseResult> allResults = new ArrayList<>();
            for (AhoCorasickAutomaton.MatchResult match : matches) {
                if (match.getKeyword() != null) {
                    List<AddressTrace> traces = addressIndex.traceUp(match.getKeyword());
                    for (AddressTrace trace : traces) {
                        if (trace != null) {
                            ParseResult result = convertTraceToResult(trace);
                            if (result != null && passFilter(result, filter)) {
                                allResults.add(result);
                            }
                        }
                    }
                }
            }

            // 如果有多个匹配结果，尝试找到最佳组合
            List<ParseResult> bestResults = findBestCombination(allResults, matches);

            return deduplicateAndSort(bestResults);

        } catch (Exception e) {
            log.error("Error parsing address: " + address, e);
            return Collections.emptyList();
        }
    }

    /**
     * 找到最佳的地址组合
     */
    private List<ParseResult> findBestCombination(List<ParseResult> allResults, List<AhoCorasickAutomaton.MatchResult> matches) {
        if (allResults.isEmpty()) {
            return allResults;
        }

        // 提取所有匹配到的关键词
        Set<String> matchedKeywords = matches.stream()
                .map(AhoCorasickAutomaton.MatchResult::getKeyword)
                .collect(Collectors.toSet());

        // 计算每个结果的匹配度
        List<ScoredResult> scoredResults = new ArrayList<>();
        for (ParseResult result : allResults) {
            int score = calculateMatchScore(result, matchedKeywords);
            scoredResults.add(new ScoredResult(result, score));
        }

        // 按分数排序，分数高的优先
        scoredResults.sort((a, b) -> Integer.compare(b.score, a.score));

        // 如果最高分的结果有多个，返回所有最高分结果
        if (scoredResults.isEmpty()) {
            return Collections.emptyList();
        }

        int maxScore = scoredResults.get(0).score;

        // 如果最高分大于1（说明有层级关系匹配），只返回最高分的结果
        if (maxScore > 1) {
            return scoredResults.stream()
                    .filter(sr -> sr.score == maxScore)
                    .map(sr -> sr.result)
                    .collect(Collectors.toList());
        }

        // 否则返回所有结果
        return allResults;
    }

    /**
     * 计算匹配分数
     */
    private int calculateMatchScore(ParseResult result, Set<String> matchedKeywords) {
        int score = 0;

        // 检查省份匹配
        if (result.getProvince() != null && matchedKeywords.contains(result.getProvince())) {
            score += 4;
        }

        // 检查城市匹配
        if (result.getCity() != null && matchedKeywords.contains(result.getCity())) {
            score += 3;
        }

        // 检查区县匹配
        if (result.getArea() != null && matchedKeywords.contains(result.getArea())) {
            score += 2;
        }

        // 检查街道匹配
        if (result.getTown() != null && matchedKeywords.contains(result.getTown())) {
            score += 1;
        }

        return score;
    }

    private ParseResult convertTraceToResult(AddressTrace trace) {
        return ParseResult.builder()
                .province(trace.getProvince() != null ? trace.getProvince().getName() : null)
                .city(trace.getCity() != null ? trace.getCity().getName() : null)
                .area(trace.getArea() != null ? trace.getArea().getName() : null)
                .town(trace.getTown() != null ? trace.getTown().getName() : null)
                .build();
    }

    private boolean passFilter(ParseResult result, FilterCondition filter) {
        if (filter == null) {
            return true;
        }

        if (filter.getProvince() != null && !filter.getProvince().equals(result.getProvince())) {
            return false;
        }
        if (filter.getCity() != null && !filter.getCity().equals(result.getCity())) {
            return false;
        }
        return filter.getArea() == null || filter.getArea().equals(result.getArea());
    }

    private List<ParseResult> deduplicateAndSort(List<ParseResult> results) {
        return results.stream()
                .distinct()
                .sorted(Comparator.comparing(ParseResult::getProvince, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ParseResult::getCity, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ParseResult::getArea, Comparator.nullsLast(String::compareTo))
                        .thenComparing(ParseResult::getTown, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    /**
     * 用于存储带分数的结果
     */
    private static class ScoredResult {
        final ParseResult result;
        final int score;

        ScoredResult(ParseResult result, int score) {
            this.result = result;
            this.score = score;
        }
    }
}