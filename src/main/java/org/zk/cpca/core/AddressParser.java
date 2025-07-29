package org.zk.cpca.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.zk.cpca.model.AddressTrace;
import org.zk.cpca.model.Area;
import org.zk.cpca.model.ParseResult;
import org.zk.cpca.model.Town;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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

    public AddressParser() {
        this(DEFAULT_JSON_FILE);
    }

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

    public ParseResult parse(String address) {
        return parse(address, null);
    }

    public ParseResult parse(String address, FilterCondition filter) {
        if (StringUtils.isBlank(address)) {
            return null;
        }

        address = AddressSupport.toFullName(address);

        try {
            List<AhoCorasickAutomaton.MatchResult> matches = automaton.findAll(address);
            if (matches == null || matches.isEmpty()) {
                return null;
            }

            // 解析唯一最佳结果
            ParseResult bestResult = parseUniqueBestResult(address, matches, filter);
            if (bestResult != null) {
                return bestResult;
            }

            return null;
        } catch (Exception e) {
            log.error("Error parsing address: " + address, e);
            return null;
        }
    }

    /**
     * 解析唯一最佳结果的核心方法
     */
    private ParseResult parseUniqueBestResult(String address,
                                              List<AhoCorasickAutomaton.MatchResult> matches,
                                              FilterCondition filter) {

        // 按位置排序匹配结果
        matches.sort(Comparator.comparingInt(AhoCorasickAutomaton.MatchResult::getStart));

        // 策略1: 从左到右构建最完整的地址链（核心策略）
        ParseResult leftToRightResult = buildLeftToRightChain(matches, filter);
        if (leftToRightResult != null) {
            return leftToRightResult;
        }

        // 策略2: 如果策略1失败，尝试基于最强匹配
        ParseResult strongestMatchResult = buildFromStrongestMatch(matches, filter);
        if (strongestMatchResult != null) {
            return strongestMatchResult;
        }

        // 策略3: 最后备选 - 返回第一个有效结果
        return buildFirstValidResult(matches, filter);
    }

    /**
     * 从左到右构建最完整的地址链 - 主要策略（优化版）
     */
    private ParseResult buildLeftToRightChain(List<AhoCorasickAutomaton.MatchResult> matches,
                                              FilterCondition filter) {

        String province = null, city = null, area = null, town = null;
        int lastUsedEnd = -1;

        // 优化：一次遍历，动态填充缺失的层级
        for (AhoCorasickAutomaton.MatchResult match : matches) {
            // 位置约束：不能离上一个匹配太远
            if (lastUsedEnd >= 0 && match.getStart() > lastUsedEnd + 100) {
                break; // 超出合理范围就停止
            }

            // 位置约束：避免与上一个匹配重叠太多
            if (lastUsedEnd >= 0 && match.getStart() < lastUsedEnd - 10) {
                continue;
            }

            List<AddressTrace> traces = addressIndex.traceUp(match.getKeyword());
            boolean matched = false;

            for (AddressTrace trace : traces) {
                // 尝试填充省级信息
                if (province == null && trace.getProvince() != null) {
                    province = trace.getProvince().getName();
                    city = trace.getCity() != null ? trace.getCity().getName() : city;
                    area = trace.getArea() != null ? trace.getArea().getName() : area;
                    town = trace.getTown() != null ? trace.getTown().getName() : town;
                    lastUsedEnd = match.getEnd();
                    matched = true;
                    break;
                }

                // 尝试填充市级信息（需要验证省级匹配）
                if (city == null && trace.getCity() != null &&
                        (province == null || (trace.getProvince() != null &&
                                province.equals(trace.getProvince().getName())))) {

                    city = trace.getCity().getName();
                    if (province == null && trace.getProvince() != null) {
                        province = trace.getProvince().getName();
                    }
                    area = trace.getArea() != null ? trace.getArea().getName() : area;
                    town = trace.getTown() != null ? trace.getTown().getName() : town;
                    lastUsedEnd = Math.max(lastUsedEnd, match.getEnd());
                    matched = true;
                    break;
                }

                // 尝试填充区级信息（需要验证上级匹配）
                if (area == null && trace.getArea() != null &&
                        isAreaValidForCityProvince(trace.getArea(), city, province)) {

                    area = trace.getArea().getName();
                    if (city == null && trace.getCity() != null) {
                        city = trace.getCity().getName();
                    }
                    if (province == null && trace.getProvince() != null) {
                        province = trace.getProvince().getName();
                    }
                    town = trace.getTown() != null ? trace.getTown().getName() : town;
                    lastUsedEnd = Math.max(lastUsedEnd, match.getEnd());
                    matched = true;
                    break;
                }

                // 尝试填充街道信息（需要验证上级匹配）
                if (town == null && trace.getTown() != null &&
                        isTownValidForAreaCityProvince(trace.getTown(), area, city, province)) {

                    town = trace.getTown().getName();
                    // 街道匹配时也要补齐上级信息
                    if (area == null && trace.getArea() != null) {
                        area = trace.getArea().getName();
                    }
                    if (city == null && trace.getCity() != null) {
                        city = trace.getCity().getName();
                    }
                    if (province == null && trace.getProvince() != null) {
                        province = trace.getProvince().getName();
                    }
                    lastUsedEnd = Math.max(lastUsedEnd, match.getEnd());
                    matched = true;
                    break;
                }
            }

            // 早期退出：如果已经找到了完整的四级地址链，直接返回
            if (province != null && city != null && area != null && town != null) {
                break;
            }

            // 如果本次匹配成功，更新lastUsedEnd的初始值
            if (matched && lastUsedEnd < 0) {
                lastUsedEnd = match.getEnd();
            }
        }

        // 构建结果
        if (province != null || city != null) {
            ParseResult result = ParseResult.builder()
                    .province(province)
                    .city(city)
                    .area(area)
                    .town(town)
                    .build();

            if (passFilter(result, filter)) {
                return result;
            }
        }

        return null;
    }

    /**
     * 基于匹配强度构建结果 - 备选策略
     */
    private ParseResult buildFromStrongestMatch(List<AhoCorasickAutomaton.MatchResult> matches,
                                                FilterCondition filter) {

        // 计算每个匹配的权重得分
        List<WeightedMatch> weightedMatches = new ArrayList<>();
        for (AhoCorasickAutomaton.MatchResult match : matches) {
            List<AddressTrace> traces = addressIndex.traceUp(match.getKeyword());
            for (AddressTrace trace : traces) {
                int weight = calculateMatchWeight(match, trace);
                weightedMatches.add(new WeightedMatch(match, trace, weight));
            }
        }

        // 按权重排序，选择最强的匹配作为基础
        weightedMatches.sort((a, b) -> Integer.compare(b.weight, a.weight));

        for (WeightedMatch weighted : weightedMatches) {
            ParseResult result = convertTraceToResult(weighted.trace);
            if (result != null && passFilter(result, filter)) {
                return result;
            }
        }

        return null;
    }

    /**
     * 计算匹配权重
     */
    private int calculateMatchWeight(AhoCorasickAutomaton.MatchResult match, AddressTrace trace) {
        int weight = 0;

        // 位置权重：越靠前权重越高
        weight += Math.max(0, 100 - match.getStart());

        // 长度权重：匹配词越长权重越高
        weight += match.getKeyword().length() * 10;

        // 层级权重：省 > 市 > 区 > 街道
        if (trace.getProvince() != null) weight += 1000;
        if (trace.getCity() != null) weight += 500;
        if (trace.getArea() != null) weight += 200;
        if (trace.getTown() != null) weight += 50;

        // 完整性权重：包含更多层级的权重更高
        int levels = 0;
        if (trace.getProvince() != null) levels++;
        if (trace.getCity() != null) levels++;
        if (trace.getArea() != null) levels++;
        if (trace.getTown() != null) levels++;
        weight += levels * 100;

        return weight;
    }

    /**
     * 构建第一个有效结果 - 最后备选
     */
    private ParseResult buildFirstValidResult(List<AhoCorasickAutomaton.MatchResult> matches,
                                              FilterCondition filter) {
        for (AhoCorasickAutomaton.MatchResult match : matches) {
            List<AddressTrace> traces = addressIndex.traceUp(match.getKeyword());
            for (AddressTrace trace : traces) {
                ParseResult result = convertTraceToResult(trace);
                if (result != null && passFilter(result, filter)) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 验证区是否属于指定的市和省
     */
    private boolean isAreaValidForCityProvince(Area area, String cityName, String provinceName) {
        if (area == null) return false;

        // 如果没有指定上级，接受任何区
        if (cityName == null && provinceName == null) return true;

        // 通过索引验证层次关系
        Area foundArea = addressIndex.getAreaByName(provinceName, cityName, area.getName());
        return foundArea != null;
    }

    /**
     * 验证街道是否属于指定的区、市、省
     */
    private boolean isTownValidForAreaCityProvince(Town town, String areaName,
                                                   String cityName, String provinceName) {
        if (town == null) return false;

        // 如果没有指定上级，接受任何街道
        if (areaName == null && cityName == null && provinceName == null) return true;

        // 通过索引验证层次关系
        Town foundTown = addressIndex.getTownByName(provinceName, cityName, areaName, town.getName());
        return foundTown != null;
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

    /**
     * 带权重的匹配结果
     */
    private static class WeightedMatch {
        final AhoCorasickAutomaton.MatchResult match;
        final AddressTrace trace;
        final int weight;

        WeightedMatch(AhoCorasickAutomaton.MatchResult match, AddressTrace trace, int weight) {
            this.match = match;
            this.trace = trace;
            this.weight = weight;
        }
    }
}