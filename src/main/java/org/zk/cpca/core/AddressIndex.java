package org.zk.cpca.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.zk.cpca.model.*;

import java.util.*;

/**
 * @author zk
 */
@Slf4j
public class AddressIndex {
    private final List<Province> provinces;
    private Set<String> keywords;

    public AddressIndex(String jsonData) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Province[] provinceArray = mapper.readValue(jsonData, Province[].class);
            this.provinces = Arrays.asList(provinceArray);
            buildKeywordSet();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON data", e);
            throw new RuntimeException("Failed to initialize AddressIndex", e);
        }
    }

    private void buildKeywordSet() {
        this.keywords = new HashSet<>();
        for (Province province : provinces) {
            if (province.getName() != null) {
                keywords.add(province.getName());
            }

            if (province.getCitys() != null) {
                for (City city : province.getCitys()) {
                    if (city.getName() != null) {
                        keywords.add(city.getName());
                    }

                    if (city.getAreas() != null) {
                        for (Area area : city.getAreas()) {
                            if (area.getName() != null) {
                                keywords.add(area.getName());
                            }

                            if (area.getTowns() != null) {
                                for (Town town : area.getTowns()) {
                                    if (town.getName() != null) {
                                        keywords.add(town.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public Set<String> getAllKeywords() {
        return Collections.unmodifiableSet(keywords);
    }

    public List<AddressTrace> traceUp(String keyword) {
        if (keyword == null || provinces == null) {
            return Collections.emptyList();
        }

        List<AddressTrace> traces = new ArrayList<>();

        for (Province province : provinces) {
            // 检查省级
            if (keyword.equals(province.getName())) {
                traces.add(new AddressTrace(province, null, null, null));
            }

            // 检查市级
            if (province.getCitys() != null) {
                for (City city : province.getCitys()) {
                    if (keyword.equals(city.getName())) {
                        traces.add(new AddressTrace(province, city, null, null));
                    }

                    // 检查区县级
                    if (city.getAreas() != null) {
                        for (Area area : city.getAreas()) {
                            if (keyword.equals(area.getName())) {
                                traces.add(new AddressTrace(province, city, area, null));
                            }

                            // 检查街道级
                            if (area.getTowns() != null) {
                                for (Town town : area.getTowns()) {
                                    if (keyword.equals(town.getName())) {
                                        traces.add(new AddressTrace(province, city, area, town));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return traces;
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

    public List<Province> getProvinces() {
        return Collections.unmodifiableList(provinces);
    }
}