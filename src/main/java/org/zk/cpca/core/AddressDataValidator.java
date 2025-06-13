package org.zk.cpca.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.zk.cpca.model.Area;
import org.zk.cpca.model.City;
import org.zk.cpca.model.Province;
import org.zk.cpca.model.Town;

import java.util.List;

/**
 * @author zk
 */
@Slf4j
public class AddressDataValidator {

    public static void validateJsonFormat(String jsonData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Province> provinces = mapper.readValue(jsonData,
                    new TypeReference<List<Province>>() {
                    });

            if (provinces == null || provinces.isEmpty()) {
                throw new IllegalArgumentException("JSON数据必须包含至少一个省份");
            }

            // 验证每个省份的数据
            for (Province province : provinces) {
                validateProvince(province);
            }

            log.info("JSON数据格式验证通过");

        } catch (JsonProcessingException e) {
            log.error("JSON格式无效", e);
            throw new IllegalArgumentException("无效的JSON格式: " + e.getMessage(), e);
        }
    }

    private static void validateProvince(Province province) {
        // 验证省级数据
        if (StringUtils.isBlank(province.getName())) {
            throw new IllegalArgumentException("省份名称不能为空");
        }
        if (StringUtils.isBlank(province.getCode())) {
            throw new IllegalArgumentException("省份代码不能为空");
        }
        if (!province.getCode().matches("\\d{9}")) {
            throw new IllegalArgumentException("省份代码必须是9位数字: " + province.getCode());
        }

        // 验证市级数据
        if (province.getCitys() != null) {
            for (City city : province.getCitys()) {
                validateCity(city);
            }
        }
    }

    private static void validateCity(City city) {
        if (StringUtils.isBlank(city.getName())) {
            throw new IllegalArgumentException("城市名称不能为空");
        }
        if (StringUtils.isBlank(city.getCode())) {
            throw new IllegalArgumentException("城市代码不能为空");
        }
        if (!city.getCode().matches("\\d{9}")) {
            throw new IllegalArgumentException("城市代码必须是9位数字: " + city.getCode());
        }

        // 验证区县级数据
        if (city.getAreas() != null) {
            for (Area area : city.getAreas()) {
                validateArea(area);
            }
        }
    }

    private static void validateArea(Area area) {
        if (StringUtils.isBlank(area.getName())) {
            throw new IllegalArgumentException("区县名称不能为空");
        }
        if (StringUtils.isBlank(area.getCode())) {
            throw new IllegalArgumentException("区县代码不能为空");
        }
        if (!area.getCode().matches("\\d{9}")) {
            throw new IllegalArgumentException("区县代码必须是9位数字: " + area.getCode());
        }

        // 验证街道/镇级数据
        if (area.getTowns() != null) {
            for (Town town : area.getTowns()) {
                validateTown(town);
            }
        }
    }

    private static void validateTown(Town town) {
        if (StringUtils.isBlank(town.getName())) {
            throw new IllegalArgumentException("街道/镇名称不能为空");
        }
        if (StringUtils.isBlank(town.getCode())) {
            throw new IllegalArgumentException("街道/镇代码不能为空");
        }
        if (!town.getCode().matches("\\d{9}")) {
            throw new IllegalArgumentException("街道/镇代码必须是9位数字: " + town.getCode());
        }
    }
}