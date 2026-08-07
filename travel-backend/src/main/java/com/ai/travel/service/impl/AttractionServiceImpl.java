package com.ai.travel.service.impl;

import com.ai.travel.entity.Attraction;
import com.ai.travel.repository.AttractionRepository;
import com.ai.travel.service.AttractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttractionServiceImpl implements AttractionService {

    private final RestTemplate restTemplate;
    private final AttractionRepository attractionRepository;

    @Value("${tmap.key}")
    private String tmapKey;

    private static final String TMAP_BASE = "https://apis.map.qq.com";

    @Override
    public List<Map<String, Object>> searchAttractions(Double lat, Double lng, String city, Integer radius, String keyword) {
        if (radius == null || radius <= 0) radius = 5000;

        // 1. 无关键词时尝试读缓存
        if (keyword == null || keyword.isEmpty()) {
            try {
                List<Attraction> cached = findCached(lat, lng, city);
                if (!cached.isEmpty()) {
                    log.info("景点缓存命中: city={}, count={}", city, cached.size());
                    return toResultList(cached);
                }
            } catch (Exception e) {
                log.warn("查询景点缓存失败，忽略: {}", e.getMessage());
            }
        }

        // 2. 调腾讯地图 API
        try {
            List<Map<String, Object>> apiResults = fetchFromTencentMap(lat, lng, city, radius, keyword);
            if (!apiResults.isEmpty()) {
                // 3. 存库
                try {
                    saveAttractions(apiResults, lat, lng, city);
                } catch (Exception e) {
                    log.warn("保存景点到 MySQL 失败: {}", e.getMessage());
                }
                return apiResults;
            }
        } catch (Exception e) {
            log.error("腾讯地图景点搜索失败: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /** 查缓存 */
    private List<Attraction> findCached(Double lat, Double lng, String city) {
        if (lat != null && lng != null && lat != 0 && lng != 0) {
            double minLat = lat - 0.05;
            double maxLat = lat + 0.05;
            double minLng = lng - 0.05;
            double maxLng = lng + 0.05;
            return attractionRepository.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Attraction>()
                            .ge(Attraction::getSearchLat, minLat)
                            .le(Attraction::getSearchLat, maxLat)
                            .ge(Attraction::getSearchLng, minLng)
                            .le(Attraction::getSearchLng, maxLng)
                            .orderByAsc(Attraction::getDistance)
                            .last("LIMIT 50")
            );
        }
        if (city != null && !city.isEmpty()) {
            String dbCity = extractCityName(city);
            return attractionRepository.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Attraction>()
                            .eq(Attraction::getCity, dbCity)
                            .orderByAsc(Attraction::getDistance)
                            .last("LIMIT 50")
            );
        }
        return Collections.emptyList();
    }

    /** 调腾讯地图 API */
    private List<Map<String, Object>> fetchFromTencentMap(Double lat, Double lng, String city, int radius, String keyword) {
        String boundary;
        if (lat != null && lng != null && lat != 0 && lng != 0) {
            boundary = "nearby(" + lat + "," + lng + "," + radius + ")";
        } else if (keyword != null && !keyword.isEmpty()) {
            Map<String, Object> geo = geocodeLocation(keyword, city);
            if (geo != null) {
                boundary = "nearby(" + geo.get("lat") + "," + geo.get("lng") + "," + radius + ")";
            } else if (city != null && !city.isEmpty()) {
                boundary = "region(" + extractCityName(city) + ",0)";
            } else {
                return Collections.emptyList();
            }
        } else if (city != null && !city.isEmpty()) {
            boundary = "region(" + extractCityName(city) + ",0)";
        } else {
            return Collections.emptyList();
        }

        String searchKeyword = (keyword != null && !keyword.isEmpty()) ? keyword : "景点";

        String url = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("key", tmapKey)
                .queryParam("keyword", searchKeyword)
                .queryParam("boundary", boundary)
                .queryParam("page_size", 20)
                .queryParam("page_index", 1)
                .queryParam("language", "cn")
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) resp.get("data");
                List<Map<String, Object>> result = new ArrayList<>();
                if (rawList != null) {
                    for (Map<String, Object> item : rawList) {
                        Map<String, Object> loc = (Map<String, Object>) item.get("location");
                        // 过滤掉酒店类 POI
                        String category = (String) item.getOrDefault("category", "");
                        String title = (String) item.get("title");
                        if (category.contains("酒店") || category.contains("住宿") || category.contains("宾馆")) {
                            continue;
                        }
                        if (title != null && (title.contains("酒店") || title.contains("宾馆"))) {
                            continue;
                        }
                        Map<String, Object> poi = new LinkedHashMap<>();
                        poi.put("id", item.get("id"));
                        poi.put("name", title);
                        poi.put("address", item.getOrDefault("address", ""));
                        poi.put("tel", item.getOrDefault("tel", ""));
                        poi.put("latitude", loc != null ? loc.get("lat") : 0);
                        poi.put("longitude", loc != null ? loc.get("lng") : 0);
                        poi.put("distance", item.getOrDefault("_distance", 0));
                        poi.put("category", category);
                        result.add(poi);
                    }
                }
                return result;
            }
            log.warn("腾讯地图景点搜索失败: {}", resp);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("腾讯地图景点搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 地理编码 */
    private Map<String, Object> geocodeLocation(String address, String city) {
        if (address == null || address.isEmpty()) return null;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/geocoder/v1")
                .queryParam("address", address)
                .queryParam("key", tmapKey);
        if (city != null && !city.isEmpty()) {
            builder.queryParam("region", extractCityName(city));
        }
        try {
            Map<String, Object> resp = restTemplate.getForObject(builder.toUriString(), Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                Map<String, Object> location = (Map<String, Object>) result.get("location");
                if (location != null) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("lat", location.get("lat"));
                    data.put("lng", location.get("lng"));
                    return data;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("地理编码异常: {} - {}", address, e.getMessage());
            return null;
        }
    }

    /** 存库 */
    private void saveAttractions(List<Map<String, Object>> list, Double lat, Double lng, String city) {
        for (Map<String, Object> item : list) {
            try {
                String poiId = (String) item.get("id");
                if (poiId == null) continue;
                Attraction existing = attractionRepository.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Attraction>()
                                .eq(Attraction::getAttractionId, poiId).last("LIMIT 1")
                );
                if (existing != null) {
                    existing.setName((String) item.get("name"));
                    existing.setAddress((String) item.get("address"));
                    existing.setTel((String) item.get("tel"));
                    existing.setLatitude(toDouble(item.get("latitude")));
                    existing.setLongitude(toDouble(item.get("longitude")));
                    existing.setDistance(toInt(item.get("distance")));
                    existing.setCategory((String) item.get("category"));
                    existing.setSearchLat(lat);
                    existing.setSearchLng(lng);
                    attractionRepository.updateById(existing);
                } else {
                    Attraction a = new Attraction();
                    a.setAttractionId(poiId);
                    a.setName((String) item.get("name"));
                    a.setAddress((String) item.get("address"));
                    a.setTel((String) item.get("tel"));
                    a.setLatitude(toDouble(item.get("latitude")));
                    a.setLongitude(toDouble(item.get("longitude")));
                    a.setDistance(toInt(item.get("distance")));
                    a.setCity(city != null ? extractCityName(city) : "");
                    a.setCategory((String) item.get("category"));
                    a.setSource("tencent_map");
                    a.setSearchLat(lat);
                    a.setSearchLng(lng);
                    attractionRepository.insert(a);
                }
            } catch (Exception e) {
                log.warn("保存景点失败: {}", e.getMessage());
            }
        }
    }

    /** 格式化输出 */
    private List<Map<String, Object>> toResultList(List<Attraction> list) {
        return list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getAttractionId());
            m.put("name", a.getName());
            m.put("address", a.getAddress());
            m.put("tel", a.getTel());
            m.put("latitude", a.getLatitude());
            m.put("longitude", a.getLongitude());
            m.put("distance", a.getDistance());
            m.put("city", a.getCity());
            m.put("category", a.getCategory());
            return m;
        }).collect(Collectors.toList());
    }

    private String extractCityName(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        int idx = raw.lastIndexOf(',');
        return idx >= 0 ? raw.substring(idx + 1).trim() : raw;
    }

    private Double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    private Integer toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
