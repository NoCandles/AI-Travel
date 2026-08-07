package com.ai.travel.service.impl;

import com.ai.travel.service.MapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {

    private final RestTemplate restTemplate;

    @Value("${tmap.key}")
    private String tmapKey;

    private static final String TMAP_BASE = "https://apis.map.qq.com";

    @Override
    public Map<String, Object> reverseGeocode(double longitude, double latitude) {
        String url = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/geocoder/v1")
                .queryParam("location", latitude + "," + longitude)
                .queryParam("key", tmapKey)
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                return (Map<String, Object>) resp.get("result");
            }
            throw new RuntimeException("逆地理编码失败");
        } catch (Exception e) {
            log.error("逆地理编码失败: {}", e.getMessage());
            throw new RuntimeException("逆地理编码失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> geocode(String address, String city) {
        if (address == null || address.trim().isEmpty()) {
            return emptyGeocode("invalid_address", "Address is empty");
        }

        String rawAddress = address.trim();
        String cityName = normalizeCityForGeocode(city);

        // 第一步：不加城市前缀，直接搜原始地址（更精准，避免跨区域景点被城市前缀干扰）
        Map<String, Object> result = callGeocodeApi(rawAddress, 0);
        if (result != null) {
            return result;
        }

        // 第二步：用城市前缀兜底（适用于常见地名需要城市消歧的场景，如"人民公园"）
        if (cityName != null && !cityName.isEmpty()) {
            result = callGeocodeApi(cityName + rawAddress, 0);
            if (result != null) {
                log.info("Geocode succeeded with city prefix: address={}, city={}", rawAddress, cityName);
                return result;
            }
        }

        // 第三步：policy=1 宽松匹配兜底
        result = callGeocodeApi(rawAddress, 1);
        if (result != null) {
            return result;
        }

        log.warn("Geocode degraded to empty result: address={}, city={}", address, city);
        return emptyGeocode("geocode_not_found", "Cannot locate " + address);
    }

    private String normalizeCityForGeocode(String city) {
        if (city == null || city.trim().isEmpty()) {
            return null;
        }
        String c = city.trim();

        // 有效行政区后缀 —— 直接返回
        if (c.endsWith("省") || c.endsWith("市") || c.endsWith("自治区")
                || c.endsWith("州") || c.endsWith("县") || c.endsWith("区")
                || c.endsWith("盟") || c.endsWith("旗")) {
            return c;
        }

        // 非城市名过滤：含路线/环线/穿越/大环等关键词的是路线名而非城市名
        if (c.contains("线") || c.contains("环线") || c.contains("穿越")
                || c.contains("走廊") || c.contains("古道") || c.contains("大道")
                || c.length() > 4) {
            log.info("City name looks like a route/scenic name, skipping: {}", c);
            return null;
        }

        return c + "市";
    }

    private Map<String, Object> callGeocodeApi(String address, int policy) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/geocoder/v1")
                .queryParam("address", address)
                .queryParam("key", tmapKey);
        if (policy == 1) {
            builder.queryParam("policy", "1");
        }

        try {
            Map<String, Object> resp = restTemplate.getForObject(builder.toUriString(), Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                Map<String, Object> location = (Map<String, Object>) result.get("location");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("location", location.get("lng") + "," + location.get("lat"));
                data.put("lat", location.get("lat"));
                data.put("lng", location.get("lng"));
                return data;
            }
            log.warn("Geocode API failed: address={}, policy={}, message={}", address, policy,
                    resp != null ? resp.get("message") : "null");
        } catch (Exception e) {
            log.error("Geocode request exception: address={}, policy={}, error={}", address, policy, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> emptyGeocode(String reason, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("location", "");
        result.put("lat", null);
        result.put("lng", null);
        result.put("fallback", true);
        result.put("reason", reason);
        result.put("message", message == null ? "" : message);
        return result;
    }

    @Override
    public Map<String, Object> searchPOI(String keyword, String city) {
        // 第一次：带城市边界搜索
        if (city != null && !city.isEmpty()) {
            Map<String, Object> result = callSearchPOI(keyword, "region(" + city + ",0)");
            if (result != null) {
                List<Map<String, Object>> pois = (List<Map<String, Object>>) result.get("pois");
                if (pois != null && !pois.isEmpty()) {
                    return result;
                }
                log.info("POI search with city boundary returned empty, retry without boundary: keyword={}, city={}", keyword, city);
            }
        }

        // 降级：不带城市边界，全国搜索
        Map<String, Object> result = callSearchPOI(keyword, null);
        if (result != null) {
            return result;
        }
        throw new RuntimeException("POI搜索失败");
    }

    private Map<String, Object> callSearchPOI(String keyword, String boundary) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("keyword", keyword)
                .queryParam("key", tmapKey);
        if (boundary != null && !boundary.isEmpty()) {
            builder.queryParam("boundary", boundary);
        }

        try {
            Map<String, Object> resp = restTemplate.getForObject(builder.toUriString(), Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) resp.get("data");
                List<Map<String, Object>> pois = new ArrayList<>();
                if (rawList != null) {
                    for (Map<String, Object> item : rawList) {
                        Map<String, Object> loc = (Map<String, Object>) item.get("location");
                        Map<String, Object> poi = new LinkedHashMap<>();
                        poi.put("name", item.get("title"));
                        poi.put("address", item.getOrDefault("address", ""));
                        poi.put("latitude", loc.get("lat"));
                        poi.put("longitude", loc.get("lng"));
                        pois.add(poi);
                    }
                }
                return Collections.singletonMap("pois", pois);
            }
            log.warn("POI search API failed: keyword={}, boundary={}, message={}",
                    keyword, boundary, resp != null ? resp.get("message") : "null");
        } catch (Exception e) {
            log.error("POI search exception: keyword={}, boundary={}, error={}", keyword, boundary, e.getMessage());
        }
        return null;
    }

    @Override
    public Map<String, Object> getRoute(String origin, String destination, String waypoints, String mode, String city) {
        // origin/destination 格式: lng,lat（前端传入）
        // 腾讯地图 API 要求 from/to 格式: lat,lng

        // 构建所有途经点列表（含起点和终点）
        List<String> allPoints = new ArrayList<>();
        allPoints.add(origin);  // lng,lat
        if (waypoints != null && !waypoints.isEmpty()) {
            for (String wp : waypoints.split(";")) {
                if (!wp.trim().isEmpty()) {
                    allPoints.add(wp.trim());
                }
            }
        }
        allPoints.add(destination);

        // 驾车模式：单次请求，支持 waypoints 参数
        if ("drive".equals(mode)) {
            return getDriveRoute(allPoints, mode);
        }

        // 非驾车模式（walk/bike/ebike/transit）：不支持 waypoints，需分段请求再合并
        return getMultiSegmentRoute(allPoints, mode, city);
    }

    /**
     * 驾车模式：直接带途经点单次请求
     */
    private Map<String, Object> getDriveRoute(List<String> allPoints, String mode) {
        String origin = allPoints.get(0);
        String destination = allPoints.get(allPoints.size() - 1);
        List<String> waypointList = allPoints.subList(1, allPoints.size() - 1);

        String fromLatLng = swapLngLat(origin);
        String toLatLng = swapLngLat(destination);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/direction/v1/driving/")
                .queryParam("from", fromLatLng)
                .queryParam("to", toLatLng)
                .queryParam("key", tmapKey)
                .queryParam("output", "json")
                .queryParam("get_mp", "1");

        if (!waypointList.isEmpty()) {
            String wps = waypointList.stream()
                    .map(this::swapLngLat)
                    .reduce((a, b) -> a + ";" + b).orElse("");
            builder.queryParam("waypoints", wps);
        }

        String fullUrl = builder.toUriString();

        log.info("驾车路线请求: {}", fullUrl);
        return callTencentRouteApi(fullUrl, false);
    }

    /**
     * 非驾车模式：分段请求，每段 A→B 单独调用，再合并结果
     */
    private Map<String, Object> getMultiSegmentRoute(List<String> allPoints, String mode, String city) {
        String apiPath;
        switch (mode) {
            case "walk":    apiPath = "/ws/direction/v1/walking/"; break;
            case "transit": apiPath = "/ws/direction/v1/transit/"; break;
            case "bike":    apiPath = "/ws/direction/v1/bicycling/"; break;
            case "ebike":   apiPath = "/ws/direction/v1/ebicycling/"; break;
            default:        apiPath = "/ws/direction/v1/walking/"; break;
        }

        List<Map<String, Object>> segmentResults = new ArrayList<>();
        double totalDistance = 0;
        double totalDuration = 0;

        // 逐段请求
        for (int i = 0; i < allPoints.size() - 1; i++) {
            String from = swapLngLat(allPoints.get(i));
            String to = swapLngLat(allPoints.get(i + 1));

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + apiPath)
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .queryParam("key", tmapKey)
                    .queryParam("output", "json");

            if ("transit".equals(mode) && city != null && !city.isEmpty()) {
                builder.queryParam("city", city);
                builder.queryParam("cityd", city);
            }

            String segUrl = builder.toUriString();
            log.info("分段{}路线请求: {}", i + 1, segUrl);

            try {
                Map<String, Object> resp = restTemplate.getForObject(segUrl, Map.class);
                if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                    Map<String, Object> result = (Map<String, Object>) resp.get("result");
                    List<Map<String, Object>> routes = (List<Map<String, Object>>) result.get("routes");
                    if (routes != null && !routes.isEmpty()) {
                        Map<String, Object> route = routes.get(0);
                        // 解码本段 polyline
                        List<double[]> decodedPoints = decodeTencentPolyline(
                                (List<Number>) route.getOrDefault("polyline", Collections.emptyList()));
                        // 解析 steps
                        List<Map<String, Object>> steps = parseSteps(
                                (List<Map<String, Object>>) route.get("steps"));

                        Map<String, Object> segData = new LinkedHashMap<>();
                        segData.put("points", decodedPoints);
                        segData.put("steps", steps);
                        segData.put("distance", ((Number) route.get("distance")).doubleValue());
                        segData.put("duration", ((Number) route.get("duration")).doubleValue());
                        segmentResults.add(segData);

                        totalDistance += ((Number) route.get("distance")).doubleValue();
                        totalDuration += ((Number) route.get("duration")).doubleValue();

                        log.info("分段{}完成: distance={}m, duration={}min, points={}, steps={}",
                                i + 1, route.get("distance"), route.get("duration"),
                                decodedPoints.size(), steps.size());
                    } else {
                        log.warn("分段{}无路线结果", i + 1);
                    }
                } else {
                    log.warn("分段{}请求失败: status={}, message={}", i + 1,
                            resp != null ? resp.get("status") : "null",
                            resp != null ? resp.get("message") : "null");
                }
            } catch (Exception e) {
                log.error("分段{}请求异常: {}", i + 1, e.getMessage());
            }
        }

        if (segmentResults.isEmpty()) {
            throw new RuntimeException("所有分段路线规划均失败");
        }

        // 合并所有分段
        Map<String, Object> mergedPath = mergeSegments(segmentResults, totalDistance, totalDuration);
        log.info("分段合并完成: totalDistance={}m, totalDuration={}min", totalDistance, totalDuration);

        return Collections.singletonMap("paths", Collections.singletonList(mergedPath));
    }

    /**
     * 合并多个分段的结果
     */
    private Map<String, Object> mergeSegments(List<Map<String, Object>> segments,
                                               double totalDistance, double totalDuration) {
        List<double[]> allPoints = new ArrayList<>();
        List<Map<String, Object>> allSteps = new ArrayList<>();
        int pointOffset = 0;

        for (int s = 0; s < segments.size(); s++) {
            Map<String, Object> seg = segments.get(s);
            List<double[]> segPoints = (List<double[]>) seg.get("points");
            List<Map<String, Object>> segSteps = (List<Map<String, Object>>) seg.get("steps");

            if (s == 0) {
                // 第一段：直接使用全部坐标点
                allPoints.addAll(segPoints);
            } else {
                // 后续段：跳过第一个点（与上一段终点重合），避免折线重复
                if (segPoints.size() > 1) {
                    allPoints.addAll(segPoints.subList(1, segPoints.size()));
                    pointOffset -= 1; // 因为少了一个点，后续 polyline_idx 需要减 1
                }
            }

            // 调整 steps 的 polyline_idx 偏移量
            for (Map<String, Object> step : segSteps) {
                List<Integer> idx = (List<Integer>) step.get("polyline_idx");
                if (idx != null && idx.size() == 2) {
                    int newStart = idx.get(0) + pointOffset;
                    int newEnd = idx.get(1) + pointOffset;
                    if (newStart < 0) newStart = 0;
                    if (newEnd < 0) newEnd = 0;
                    step.put("polyline_idx", Arrays.asList(newStart, newEnd));
                }
                allSteps.add(step);
            }

            pointOffset += segPoints.size();
        }

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("distance", totalDistance);
        path.put("duration", Math.round(totalDuration));
        // 返回已解码的坐标点（前端直接使用）
        path.put("polyline", allPoints);
        path.put("polyline_decoded", true);
        path.put("steps", allSteps);

        return path;
    }

    /**
     * 解码腾讯地图压缩 polyline 为坐标点数组
     */
    private List<double[]> decodeTencentPolyline(List<Number> encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.size() < 2) return points;

        // 前两个值是绝对坐标（不是乘以1e6的整数，而是原始经纬度）
        double prevLat = encoded.get(0).doubleValue();
        double prevLng = encoded.get(1).doubleValue();
        points.add(new double[]{prevLat, prevLng});

        for (int i = 2; i < encoded.size(); i += 2) {
            prevLat += encoded.get(i).doubleValue() / 1000000.0;
            prevLng += encoded.get(i + 1).doubleValue() / 1000000.0;
            points.add(new double[]{prevLat, prevLng});
        }

        return points;
    }

    /**
     * 解析 steps 列表
     */
    private List<Map<String, Object>> parseSteps(List<Map<String, Object>> rawSteps) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (rawSteps == null) return steps;

        for (Map<String, Object> step : rawSteps) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("instruction", step.getOrDefault("instruction", ""));
            s.put("distance", step.getOrDefault("distance", 0));
            s.put("duration", step.getOrDefault("duration", 0));
            s.put("mode", step.getOrDefault("mode", ""));
            s.put("road_name", step.getOrDefault("road_name", ""));
            s.put("polyline_idx", step.getOrDefault("polyline_idx", Collections.emptyList()));
            steps.add(s);
        }
        return steps;
    }

    /**
     * 调用腾讯地图路线 API 并解析结果
     */
    private Map<String, Object> callTencentRouteApi(String url, boolean polylineDecoded) {
        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            log.info("腾讯地图路线响应 status={}", resp != null ? resp.get("status") : "null");

            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                Map<String, Object> result = (Map<String, Object>) resp.get("result");
                List<Map<String, Object>> routes = (List<Map<String, Object>>) result.get("routes");
                List<Map<String, Object>> paths = new ArrayList<>();
                if (routes != null) {
                    for (Map<String, Object> route : routes) {
                        Map<String, Object> path = new LinkedHashMap<>();
                        path.put("distance", route.get("distance"));
                        path.put("duration", route.get("duration"));
                        path.put("polyline", route.getOrDefault("polyline", Collections.emptyList()));
                        path.put("polyline_decoded", polylineDecoded);
                        path.put("steps", parseSteps((List<Map<String, Object>>) route.get("steps")));
                        paths.add(path);
                    }
                }
                log.info("路线解析完成: 共{}条路线", paths.size());
                return Collections.singletonMap("paths", paths);
            }
            throw new RuntimeException("路径规划失败：" + resp.get("message"));
        } catch (Exception e) {
            log.error("路径规划失败：{}", e.getMessage());
            throw new RuntimeException("路径规划失败：" + e.getMessage());
        }
    }

    /**
     * lng,lat 格式转换为 lat,lng（腾讯地图 API 要求）
     */
    private String swapLngLat(String lngLat) {
        String[] parts = lngLat.split(",");
        if (parts.length == 2) {
            return parts[1] + "," + parts[0];
        }
        return lngLat;
    }

    @Override
    public Map<String, Object> searchNearbyHotels(double longitude, double latitude, String city, int radius, int pageIndex) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("keyword", "酒店")
                .queryParam("boundary", "nearby(" + latitude + "," + longitude + "," + radius + ")")
                .queryParam("key", tmapKey)
                .queryParam("order_by", "_distance")
                .queryParam("page_index", pageIndex);

        try {
            Map<String, Object> resp = restTemplate.getForObject(builder.toUriString(), Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                return resp;
            }
            throw new RuntimeException("附近酒店搜索失败");
        } catch (Exception e) {
            log.error("附近酒店搜索失败: {}", e.getMessage());
            throw new RuntimeException("附近酒店搜索失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getWeather(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/weather/v1/")
                .queryParam("location", latitude + "," + longitude)
                .queryParam("key", tmapKey)
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (resp != null && Integer.valueOf(0).equals(resp.get("status"))) {
                return (Map<String, Object>) resp.get("result");
            }
            throw new RuntimeException("天气查询失败");
        } catch (Exception e) {
            log.error("天气查询失败: {}", e.getMessage());
            throw new RuntimeException("天气查询失败: " + e.getMessage());
        }
    }
}
