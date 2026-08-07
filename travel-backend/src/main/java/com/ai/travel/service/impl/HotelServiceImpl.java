package com.ai.travel.service.impl;

import com.ai.travel.entity.Hotel;
import com.ai.travel.repository.HotelRepository;
import com.ai.travel.service.HotelService;
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
public class HotelServiceImpl implements HotelService {

    private final RestTemplate restTemplate;
    private final HotelRepository hotelRepository;

    @Value("${tmap.key}")
    private String tmapKey;

    private static final String TMAP_BASE = "https://apis.map.qq.com";

    @Override
    public List<Map<String, Object>> searchHotels(Double lat, Double lng, String city, Integer radius, String keyword) {
        if (radius == null || radius <= 0) radius = 3000;

        // 1. 尝试从 MySQL 读取缓存（有关键词时跳过缓存，走精确 nearby 搜索）
        if (keyword == null || keyword.isEmpty()) {
            try {
                List<Hotel> cached = findCachedHotels(lat, lng, city);
                if (!cached.isEmpty()) {
                    log.info("酒店缓存命中: city={}, count={}", city, cached.size());
                    return toResultList(cached);
                }
            } catch (Exception e) {
                log.warn("查询酒店缓存失败，忽略，直接调 API: {}", e.getMessage());
            }
        }

        // 2. 调腾讯地图 API（有关键词时用地理编码获取坐标，走 nearby）
        try {
            List<Map<String, Object>> apiHotels = fetchFromTencentMap(lat, lng, city, radius, keyword);
            if (!apiHotels.isEmpty()) {
                // 3. 存库（有则更新，无则插入）
                try {
                    saveHotels(apiHotels, lat, lng, city);
                } catch (Exception e) {
                    log.warn("保存酒店到 MySQL 失败: {}", e.getMessage());
                }
                return apiHotels;
            }
        } catch (Exception e) {
            log.error("腾讯地图酒店搜索失败: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    /**
     * 从 MySQL 查询缓存的酒店
     */
    private List<Hotel> findCachedHotels(Double lat, Double lng, String city) {
        // 有坐标时按坐标范围查，否则按城市查
        if (lat != null && lng != null && lat != 0 && lng != 0) {
            // 搜索附近约 0.05 度（约 5km）范围内的缓存
            double minLat = lat - 0.05;
            double maxLat = lat + 0.05;
            double minLng = lng - 0.05;
            double maxLng = lng + 0.05;

            return hotelRepository.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Hotel>()
                            .ge(Hotel::getSearchLat, minLat)
                            .le(Hotel::getSearchLat, maxLat)
                            .ge(Hotel::getSearchLng, minLng)
                            .le(Hotel::getSearchLng, maxLng)
                            .orderByAsc(Hotel::getDistance)
                            .last("LIMIT 50")
            );
        }

        // 没有坐标，按城市查
        if (city != null && !city.isEmpty()) {
            String dbCity = extractCityName(city);
            return hotelRepository.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Hotel>()
                            .eq(Hotel::getCity, dbCity)
                            .orderByAsc(Hotel::getDistance)
                            .last("LIMIT 50")
            );
        }

        return Collections.emptyList();
    }

    /**
     * 调腾讯地图 API 搜索酒店
     */
    private List<Map<String, Object>> fetchFromTencentMap(Double lat, Double lng, String city, int radius, String keyword) {
        String boundary;
        if (lat != null && lng != null && lat != 0 && lng != 0) {
            boundary = "nearby(" + lat + "," + lng + "," + radius + ")";
        } else if (keyword != null && !keyword.isEmpty()) {
            // 有关键词（景点名）但没坐标 → 地理编码获取坐标 → nearby
            Map<String, Object> geo = geocodeLocation(keyword, city);
            if (geo != null) {
                boundary = "nearby(" + geo.get("lat") + "," + geo.get("lng") + "," + radius + ")";
            } else if (city != null && !city.isEmpty()) {
                String regionCity = extractCityName(city);
                boundary = "region(" + regionCity + ",0)";
            } else {
                return Collections.emptyList();
            }
        } else if (city != null && !city.isEmpty()) {
            String regionCity = extractCityName(city);
            boundary = "region(" + regionCity + ",0)";
        } else {
            return Collections.emptyList();
        }

        String searchKeyword = (keyword != null && !keyword.isEmpty()) ? keyword : "酒店";

        String url = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("key", tmapKey)
                .queryParam("keyword", searchKeyword)
                .queryParam("boundary", boundary)
                .queryParam("page_size", 20)
                .queryParam("page_index", 1)
                .queryParam("language", "cn")
                .queryParam("filter", "category=酒店")
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) resp.get("data");
                List<Map<String, Object>> result = new ArrayList<>();
                if (rawList != null) {
                    for (Map<String, Object> item : rawList) {
                        Map<String, Object> loc = (Map<String, Object>) item.get("location");
                        Map<String, Object> hotel = new LinkedHashMap<>();
                        hotel.put("hotelId", item.get("id"));
                        hotel.put("name", item.get("title"));
                        hotel.put("address", item.getOrDefault("address", ""));
                        hotel.put("tel", item.getOrDefault("tel", ""));
                        hotel.put("latitude", loc != null ? loc.get("lat") : 0);
                        hotel.put("longitude", loc != null ? loc.get("lng") : 0);
                        hotel.put("distance", item.getOrDefault("_distance", 0));
                        hotel.put("city", city != null ? city : "");
                        hotel.put("category", item.getOrDefault("category", ""));
                        hotel.put("source", "tencent_map");
                        result.add(hotel);
                    }
                }
                return result;
            }
            log.warn("腾讯地图酒店搜索失败: {}", resp);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("腾讯地图酒店搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 通用 POI 搜索（景点、美食等）
     */
    @Override
    public List<Map<String, Object>> searchPOI(String keyword, Double lat, Double lng, String city, Integer radius) {
        if (keyword == null || keyword.isEmpty()) return Collections.emptyList();
        if (radius == null || radius <= 0) radius = 5000;

        String boundary;
        if (lat != null && lng != null && lat != 0 && lng != 0) {
            boundary = "nearby(" + lat + "," + lng + "," + radius + ")";
        } else if (city != null && !city.isEmpty()) {
            boundary = "region(" + city + ",0)";
        } else {
            return Collections.emptyList();
        }

        String url = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("key", tmapKey)
                .queryParam("keyword", keyword)
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
                        Map<String, Object> poi = new LinkedHashMap<>();
                        poi.put("id", item.get("id"));
                        poi.put("name", item.get("title"));
                        poi.put("address", item.getOrDefault("address", ""));
                        poi.put("tel", item.getOrDefault("tel", ""));
                        poi.put("latitude", loc != null ? loc.get("lat") : 0);
                        poi.put("longitude", loc != null ? loc.get("lng") : 0);
                        poi.put("distance", item.getOrDefault("_distance", 0));
                        poi.put("category", item.getOrDefault("category", ""));
                        result.add(poi);
                    }
                }
                return result;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("POI搜索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 批量保存酒店到 MySQL（有则更新，无则插入）
     */
    private void saveHotels(List<Map<String, Object>> hotels, Double lat, Double lng, String city) {
        for (Map<String, Object> item : hotels) {
            try {
                String hotelId = (String) item.get("hotelId");
                if (hotelId == null) continue;

                // 检查是否已存在
                Hotel existing = hotelRepository.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Hotel>()
                                .eq(Hotel::getHotelId, hotelId)
                                .last("LIMIT 1")
                );

                if (existing != null) {
                    // 更新
                    existing.setName((String) item.get("name"));
                    existing.setAddress((String) item.get("address"));
                    existing.setTel((String) item.get("tel"));
                    existing.setLatitude(toDouble(item.get("latitude")));
                    existing.setLongitude(toDouble(item.get("longitude")));
                    existing.setDistance(toInt(item.get("distance")));
                    existing.setCity(city != null ? city : (String) item.get("city"));
                    existing.setCategory((String) item.get("category"));
                    existing.setSearchLat(lat);
                    existing.setSearchLng(lng);
                    hotelRepository.updateById(existing);
                } else {
                    // 新增
                    Hotel hotel = new Hotel();
                    hotel.setHotelId(hotelId);
                    hotel.setName((String) item.get("name"));
                    hotel.setAddress((String) item.get("address"));
                    hotel.setTel((String) item.get("tel"));
                    hotel.setLatitude(toDouble(item.get("latitude")));
                    hotel.setLongitude(toDouble(item.get("longitude")));
                    hotel.setDistance(toInt(item.get("distance")));
                    hotel.setCity(city != null ? city : (String) item.get("city"));
                    hotel.setCategory((String) item.get("category"));
                    hotel.setSource("tencent_map");
                    hotel.setSearchLat(lat);
                    hotel.setSearchLng(lng);
                    hotel.setTags(extractTags(item));
                    hotelRepository.insert(hotel);
                }
            } catch (Exception e) {
                log.warn("保存酒店失败: {}", e.getMessage());
            }
        }
    }

    /** 提取标签 */
    private String extractTags(Map<String, Object> item) {
        String category = (String) item.get("category");
        String name = (String) item.get("name");
        Set<String> tags = new LinkedHashSet<>();
        if (category != null) {
            for (String s : category.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) tags.add(t);
            }
        }
        if (name != null) {
            if (name.contains("民宿")) tags.add("民宿");
            else if (name.contains("公寓")) tags.add("公寓");
            else if (name.contains("青年") || name.contains("青旅")) tags.add("青年旅舍");
            else if (name.contains("酒店")) tags.add("酒店");
        }
        return String.join(",", tags);
    }

    /** 转为前端统一格式 */
    private List<Map<String, Object>> toResultList(List<Hotel> hotels) {
        return hotels.stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hotelId", h.getHotelId());
            m.put("name", h.getName());
            m.put("address", h.getAddress());
            m.put("tel", h.getTel());
            m.put("latitude", h.getLatitude());
            m.put("longitude", h.getLongitude());
            m.put("distance", h.getDistance());
            m.put("city", h.getCity());
            m.put("category", h.getCategory());
            m.put("tags", h.getTags());
            m.put("source", h.getSource());
            return m;
        }).collect(Collectors.toList());
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

    /**
     * 地理编码：景点名 → 坐标
     * 调腾讯地图 geocoder 接口
     */
    private Map<String, Object> geocodeLocation(String address, String city) {
        if (address == null || address.isEmpty()) return null;

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/geocoder/v1")
                .queryParam("address", address)
                .queryParam("key", tmapKey);
        if (city != null && !city.isEmpty()) {
            String regionCity = extractCityName(city);
            builder.queryParam("region", regionCity);
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
                    log.info("地理编码: {} → {},{}", address, location.get("lat"), location.get("lng"));
                    return data;
                }
            }
            log.warn("地理编码失败: {} - {}", address, resp);
            return null;
        } catch (Exception e) {
            log.warn("地理编码异常: {} - {}", address, e.getMessage());
            return null;
        }
    }

    /**
     * 从 "曾厝垵,厦门" 中提取城市名 "厦门"
     * 取最后一段（逗号分隔），无逗号则原样返回
     */
    private String extractCityName(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        int idx = raw.lastIndexOf(',');
        if (idx >= 0) {
            String city = raw.substring(idx + 1).trim();
            return city.isEmpty() ? raw : city;
        }
        return raw;
    }
}
