package org.zk.cpca.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.zk.cpca.model.*;

import java.util.*;

/**
 * 反查索引 AddressIndex
 * 支持百万级 traceUp 精准查找
 *
 * @author zk
 */
@Slf4j
public class AddressIndex {
    private final List<Province> provinces;
    private final Set<String> keywords;
    private final Map<String, List<AddressTrace>> reverseIndex;

    public AddressIndex(String jsonData) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Province[] provinceArray = mapper.readValue(jsonData, Province[].class);
            this.provinces = Arrays.asList(provinceArray);
            this.keywords = new HashSet<>();
            this.reverseIndex = new HashMap<>();
            buildKeywordSetAndIndex();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON data", e);
            throw new RuntimeException("Failed to initialize AddressIndex", e);
        }
    }

    /**
     * 关键词收集 + 反查索引构建
     */
    private void buildKeywordSetAndIndex() {
        for (Province province : provinces) {
            if (province.getName() != null) {
                keywords.add(province.getName());
                addToReverseIndex(province.getName(), new AddressTrace(province, null, null, null));
            }

            for (City city : safe(province.getCitys())) {
                if (city.getName() != null) {
                    keywords.add(city.getName());
                    addToReverseIndex(city.getName(), new AddressTrace(province, city, null, null));
                }

                for (Area area : safe(city.getAreas())) {
                    if (area.getName() != null) {
                        keywords.add(area.getName());
                        addToReverseIndex(area.getName(), new AddressTrace(province, city, area, null));
                    }

                    for (Town town : safe(area.getTowns())) {
                        if (town.getName() != null) {
                            keywords.add(town.getName());
                            addToReverseIndex(town.getName(), new AddressTrace(province, city, area, town));
                        }
                    }
                }
            }
        }
    }

    private void addToReverseIndex(String keyword, AddressTrace trace) {
        if (keyword == null) {
            return;
        }
        reverseIndex.computeIfAbsent(keyword, k -> new ArrayList<>()).add(trace);
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 获取全部关键词（用于构建 AC 自动机）
     */
    public Set<String> getAllKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    /**
     * 高性能反查：从关键词查出所有 AddressTrace 路径
     */
    public List<AddressTrace> traceUp(String keyword) {
        if (keyword == null) {
            return Collections.emptyList();
        }
        return reverseIndex.getOrDefault(keyword, Collections.emptyList());
    }

    public List<Province> getProvinces() {
        return Collections.unmodifiableList(provinces);
    }

    public Province getProvinceByName(String name) {
        if (name == null || provinces == null) {
            return null;
        }
        return provinces.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    public City getCityByName(String provinceName, String cityName) {
        Province province = getProvinceByName(provinceName);
        if (province == null || cityName == null || province.getCitys() == null) {
            return null;
        }
        return province.getCitys().stream()
                .filter(c -> cityName.equals(c.getName()))
                .findFirst()
                .orElse(null);
    }

    public Area getAreaByName(String provinceName, String cityName, String areaName) {
        City city = getCityByName(provinceName, cityName);
        if (city == null || areaName == null || city.getAreas() == null) {
            return null;
        }
        return city.getAreas().stream()
                .filter(a -> areaName.equals(a.getName()))
                .findFirst()
                .orElse(null);
    }

    public Town getTownByName(String provinceName, String cityName, String areaName, String townName) {
        Area area = getAreaByName(provinceName, cityName, areaName);
        if (area == null || townName == null || area.getTowns() == null) {
            return null;
        }
        return area.getTowns().stream()
                .filter(t -> townName.equals(t.getName()))
                .findFirst()
                .orElse(null);
    }
}