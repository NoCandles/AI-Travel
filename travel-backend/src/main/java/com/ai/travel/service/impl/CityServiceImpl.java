package com.ai.travel.service.impl;

import com.ai.travel.dto.CityGroupDTO;
import com.ai.travel.entity.City;
import com.ai.travel.repository.CityRepository;
import com.ai.travel.service.CityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class CityServiceImpl implements CityService {

    private final CityRepository cityRepository;

    @Override
    public List<City> getAllCities() {
        LambdaQueryWrapper<City> wrapper = new LambdaQueryWrapper<City>()
                .orderByAsc(City::getPinyinShort)
                .orderByAsc(City::getPinyin);
        return cityRepository.selectList(wrapper);
    }

    @Override
    public List<City> getHotCities() {
        LambdaQueryWrapper<City> wrapper = new LambdaQueryWrapper<City>()
                .gt(City::getHot, 0)
                .orderByDesc(City::getHot)
                .orderByAsc(City::getPinyin)
                .last("LIMIT 5");
        return cityRepository.selectList(wrapper);
    }

    @Override
    public CityGroupDTO getGroupedCities() {
        // 查询所有城市
        List<City> allCities = getAllCities();

        CityGroupDTO dto = new CityGroupDTO();
        dto.setTotalCount(allCities.size());

        // 热门城市 (hot > 0, 按 hot 降序，最多5个)
        List<City> hotList = allCities.stream()
                .filter(c -> c.getHot() != null && c.getHot() > 0)
                .sorted(Comparator.comparing(City::getHot).reversed())
                .limit(5)
                .collect(Collectors.toList());

        dto.setHotCities(hotList.stream()
                .map(c -> new CityGroupDTO.CityItem(
                        String.valueOf(c.getId()),
                        c.getName(),
                        c.getPinyin()))
                .collect(Collectors.toList()));

        // 按拼音首字母分组
        Map<String, List<City>> groups = new TreeMap<>();
        for (City city : allCities) {
            String letter = city.getPinyinShort();
            if (letter == null || letter.isEmpty()) {
                continue;
            }
            groups.computeIfAbsent(letter.toUpperCase(), k -> new ArrayList<>()).add(city);
        }

        List<CityGroupDTO.CityLetterGroup> groupCities = new ArrayList<>();
        for (Map.Entry<String, List<City>> entry : groups.entrySet()) {
            CityGroupDTO.CityLetterGroup group = new CityGroupDTO.CityLetterGroup();
            group.setLetter(entry.getKey());
            group.setCities(entry.getValue().stream()
                    .sorted(Comparator.comparing(City::getPinyin))
                    .map(c -> new CityGroupDTO.CityItem(
                            String.valueOf(c.getId()),
                            c.getName(),
                            c.getPinyin()))
                    .collect(Collectors.toList()));
            groupCities.add(group);
        }
        dto.setGroupCities(groupCities);

        log.info("城市分组查询完成: 总数={}, 热门={}, 分组数={}",
                dto.getTotalCount(), dto.getHotCities().size(), groupCities.size());

        return dto;
    }

    @Override
    public City getNearestCity(double lng, double lat) {
        List<City> cities = getAllCities();
        if (cities.isEmpty()) {
            return null;
        }

        City nearest = null;
        double minDist = Double.MAX_VALUE;

        for (City city : cities) {
            if (city.getLng() == null || city.getLat() == null) {
                continue;
            }
            double dist = haversine(lat, lng, city.getLat().doubleValue(), city.getLng().doubleValue());
            if (dist < minDist) {
                minDist = dist;
                nearest = city;
            }
        }

        log.info("最近城市查询: ({}, {}) -> {} (距离 {:.1f} km)", lng, lat,
                nearest != null ? nearest.getName() : "null", minDist);
        return nearest;
    }

    /** Haversine 公式计算两点间距离 (km) */
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0; // 地球半径 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    public City findCityByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String query = name.trim();

        LambdaQueryWrapper<City> wrapper = new LambdaQueryWrapper<City>()
                .eq(City::getName, query);
        City exactMatch = cityRepository.selectOne(wrapper);
        if (exactMatch != null) {
            return exactMatch;
        }

        String provincialCapital = getProvincialCapital(query);
        if (provincialCapital != null) {
            wrapper = new LambdaQueryWrapper<City>()
                    .eq(City::getName, provincialCapital);
            City capitalMatch = cityRepository.selectOne(wrapper);
            if (capitalMatch != null) {
                return capitalMatch;
            }
        }

        wrapper = new LambdaQueryWrapper<City>()
                .like(City::getName, query)
                .orderByDesc(City::getHot)
                .orderByAsc(City::getName);
        List<City> likeMatches = cityRepository.selectList(wrapper);
        if (!likeMatches.isEmpty()) {
            return likeMatches.get(0);
        }

        String[] prefixes = {"省", "市", "自治区", "州", "地区", "县", "区"};
        for (String prefix : prefixes) {
            if (query.contains(prefix)) {
                String stripped = query.replace(prefix, "");
                wrapper = new LambdaQueryWrapper<City>()
                        .eq(City::getName, stripped);
                City strippedMatch = cityRepository.selectOne(wrapper);
                if (strippedMatch != null) {
                    return strippedMatch;
                }

                provincialCapital = getProvincialCapital(stripped);
                if (provincialCapital != null) {
                    wrapper = new LambdaQueryWrapper<City>()
                            .eq(City::getName, provincialCapital);
                    City capitalMatch = cityRepository.selectOne(wrapper);
                    if (capitalMatch != null) {
                        return capitalMatch;
                    }
                }

                wrapper = new LambdaQueryWrapper<City>()
                        .like(City::getName, stripped)
                        .orderByDesc(City::getHot)
                        .orderByAsc(City::getName);
                likeMatches = cityRepository.selectList(wrapper);
                if (!likeMatches.isEmpty()) {
                    return likeMatches.get(0);
                }
            }
        }

        wrapper = new LambdaQueryWrapper<City>()
                .likeRight(City::getName, query)
                .orderByDesc(City::getHot)
                .orderByAsc(City::getName);
        likeMatches = cityRepository.selectList(wrapper);
        if (!likeMatches.isEmpty()) {
            return likeMatches.get(0);
        }

        return null;
    }

    private String getProvincialCapital(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String n = name.trim();
        Map<String, String> capitals = new HashMap<>();
        capitals.put("北京", "北京");
        capitals.put("天津", "天津");
        capitals.put("上海", "上海");
        capitals.put("重庆", "重庆");
        capitals.put("河北", "石家庄");
        capitals.put("山西", "太原");
        capitals.put("辽宁", "沈阳");
        capitals.put("吉林", "长春");
        capitals.put("黑龙江", "哈尔滨");
        capitals.put("江苏", "南京");
        capitals.put("浙江", "杭州");
        capitals.put("安徽", "合肥");
        capitals.put("福建", "福州");
        capitals.put("江西", "南昌");
        capitals.put("山东", "济南");
        capitals.put("河南", "郑州");
        capitals.put("湖北", "武汉");
        capitals.put("湖南", "长沙");
        capitals.put("广东", "广州");
        capitals.put("海南", "海口");
        capitals.put("四川", "成都");
        capitals.put("贵州", "贵阳");
        capitals.put("云南", "昆明");
        capitals.put("陕西", "西安");
        capitals.put("甘肃", "兰州");
        capitals.put("青海", "西宁");
        capitals.put("台湾", "台北");
        capitals.put("内蒙古", "呼和浩特");
        capitals.put("广西", "南宁");
        capitals.put("西藏", "拉萨");
        capitals.put("宁夏", "银川");
        capitals.put("新疆", "乌鲁木齐");
        capitals.put("香港", "香港");
        capitals.put("澳门", "澳门");
        return capitals.get(n);
    }

    @Override
    public Map<String, Object> getCityCoords(String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        City city = findCityByName(name);
        if (city != null && city.getLat() != null && city.getLng() != null) {
            result.put("found", true);
            result.put("cityName", city.getName());
            result.put("lat", city.getLat().doubleValue());
            result.put("lng", city.getLng().doubleValue());
            log.info("城市坐标查询成功: name={} -> lat={}, lng={}", name, city.getLat(), city.getLng());
        } else {
            result.put("found", false);
            result.put("cityName", null);
            result.put("lat", null);
            result.put("lng", null);
            log.warn("城市坐标查询失败: name={}", name);
        }
        return result;
    }
}
