package com.ai.travel.service.impl;

import com.ai.travel.service.MapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
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
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (isTencentSuccess(resp)) {
                return castMap(resp.get("result"));
            }
            String apiMessage = tencentMessage(resp);
            log.warn("Reverse geocode failed: lat={}, lng={}, message={}", latitude, longitude, apiMessage);
            throw new RuntimeException("Reverse geocode failed: " + apiMessage);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Reverse geocode exception: lat={}, lng={}, error={}", latitude, longitude, e.getMessage());
            throw new RuntimeException("Reverse geocode failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> geocode(String address, String city) {
        if (address == null || address.trim().isEmpty()) {
            return emptyGeocode("invalid_address", "Address is empty");
        }

        String rawAddress = address.trim();
        String cityName = normalizeCityForGeocode(city);

        // 腾讯地图 geocoder/v1 要求 address 中至少包含城市名（默认 policy=0），
        // 且 WebService API 不支持 region 参数，因此将城市名拼接到地址前。
        if (cityName != null && !cityName.isEmpty()) {
            Map<String, Object> result = callGeocodeApi(cityName + rawAddress, 0);
            if (result != null) {
                return result;
            }
            log.info("Geocode with city failed, retry without city: address={}, city={}", rawAddress, cityName);
        }

        // 地址本身可能已包含城市信息
        Map<String, Object> result = callGeocodeApi(rawAddress, 0);
        if (result != null) {
            return result;
        }

        // 宽松模式：允许地址中缺失城市，提升冷门景点/自然地貌的解析成功率
        result = callGeocodeApi(rawAddress, 1);
        if (result != null) {
            return result;
        }

        log.warn("Geocode degraded to empty result: address={}, city={}", address, city);
        return emptyGeocode("geocode_not_found", "Cannot locate " + address);
    }

    private Map<String, Object> callGeocodeApi(String address, int policy) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/geocoder/v1")
                .queryParam("address", address)
                .queryParam("key", tmapKey);
        if (policy == 1) {
            builder.queryParam("policy", "1");
        }

        try {
            Map<String, Object> resp = restTemplate.getForObject(toEncodedUri(builder), Map.class);
            if (isTencentSuccess(resp)) {
                Map<String, Object> resultMap = castMap(resp.get("result"));
                Map<String, Object> location = castMap(resultMap.get("location"));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("location", location.get("lng") + "," + location.get("lat"));
                data.put("lat", location.get("lat"));
                data.put("lng", location.get("lng"));
                return data;
            }
            log.warn("Geocode API failed: address={}, policy={}, message={}", address, policy, tencentMessage(resp));
        } catch (Exception e) {
            log.error("Geocode request exception: address={}, policy={}, error={}", address, policy, e.getMessage());
        }
        return null;
    }

    /**
     * 将省份/自治区/直辖市名称归一化为可用于地理编码的城市名。
     * WebService geocoder/v1 要求 address 中包含城市名，因此传入省份时需要映射到省会/首府。
     */
    private String normalizeCityForGeocode(String city) {
        if (city == null || city.trim().isEmpty()) {
            return null;
        }
        String c = city.trim();
        switch (c) {
            case "北京": case "北京市": return "北京";
            case "上海": case "上海市": return "上海";
            case "天津": case "天津市": return "天津";
            case "重庆": case "重庆市": return "重庆";
            case "新疆": case "新疆维吾尔自治区": case "新疆自治区": return "乌鲁木齐";
            case "西藏": case "西藏自治区": return "拉萨";
            case "内蒙古": case "内蒙古自治区": return "呼和浩特";
            case "广西": case "广西壮族自治区": return "南宁";
            case "宁夏": case "宁夏回族自治区": return "银川";
            case "香港": case "香港特别行政区": return "香港";
            case "澳门": case "澳门特别行政区": return "澳门";
            case "河北": case "河北省": return "石家庄";
            case "山西": case "山西省": return "太原";
            case "辽宁": case "辽宁省": return "沈阳";
            case "吉林": case "吉林省": return "长春";
            case "黑龙江": case "黑龙江省": return "哈尔滨";
            case "江苏": case "江苏省": return "南京";
            case "浙江": case "浙江省": return "杭州";
            case "安徽": case "安徽省": return "合肥";
            case "福建": case "福建省": return "福州";
            case "江西": case "江西省": return "南昌";
            case "山东": case "山东省": return "济南";
            case "河南": case "河南省": return "郑州";
            case "湖北": case "湖北省": return "武汉";
            case "湖南": case "湖南省": return "长沙";
            case "广东": case "广东省": return "广州";
            case "海南": case "海南省": return "海口";
            case "四川": case "四川省": return "成都";
            case "贵州": case "贵州省": return "贵阳";
            case "云南": case "云南省": return "昆明";
            case "陕西": case "陕西省": return "西安";
            case "甘肃": case "甘肃省": return "兰州";
            case "青海": case "青海省": return "西宁";
            case "台湾": case "台湾省": return "台北";
            default: return c;
        }
    }

    @Override
    public Map<String, Object> searchPOI(String keyword, String city) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("keyword", keyword)
                .queryParam("key", tmapKey);
        if (city != null && !city.isEmpty()) {
            builder.queryParam("boundary", "region(" + city + ",0)");
        }

        try {
            Map<String, Object> resp = restTemplate.getForObject(toEncodedUri(builder), Map.class);
            if (isTencentSuccess(resp)) {
                List<Map<String, Object>> rawList = castList(resp.get("data"));
                List<Map<String, Object>> pois = new ArrayList<>();
                if (rawList != null) {
                    for (Map<String, Object> item : rawList) {
                        Map<String, Object> loc = castMap(item.get("location"));
                        if (loc == null) {
                            continue;
                        }
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
            log.warn("POI search degraded to empty result: keyword={}, city={}, message={}", keyword, city, tencentMessage(resp));
            return Collections.singletonMap("pois", Collections.emptyList());
        } catch (Exception e) {
            log.warn("POI search exception, degraded to empty result: keyword={}, city={}, error={}", keyword, city, e.getMessage());
            return Collections.singletonMap("pois", Collections.emptyList());
        }
    }

    @Override
    public Map<String, Object> getRoute(String origin, String destination, String waypoints, String mode, String city) {
        List<String> allPoints = buildRoutePoints(origin, destination, waypoints);
        if (allPoints.size() < 2) {
            return emptyRoute("invalid_points", "At least two valid route points are required");
        }

        String routeMode = normalizeRouteMode(mode);
        try {
            if ("drive".equals(routeMode)) {
                return getDriveRoute(allPoints);
            }
            return getMultiSegmentRoute(allPoints, routeMode, city);
        } catch (Exception e) {
            log.warn("Route planning degraded: mode={}, points={}, error={}", routeMode, allPoints.size(), e.getMessage());
            return emptyRoute("route_planning_failed", e.getMessage());
        }
    }

    private Map<String, Object> getDriveRoute(List<String> allPoints) {
        String origin = allPoints.get(0);
        String destination = allPoints.get(allPoints.size() - 1);
        List<String> waypointList = allPoints.subList(1, allPoints.size() - 1);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/direction/v1/driving/")
                .queryParam("from", toTMapPoint(origin))
                .queryParam("to", toTMapPoint(destination))
                .queryParam("key", tmapKey)
                .queryParam("output", "json")
                .queryParam("get_mp", "1");

        if (!waypointList.isEmpty()) {
            String wps = waypointList.stream()
                    .map(this::toTMapPoint)
                    .reduce((a, b) -> a + ";" + b)
                    .orElse("");
            builder.queryParam("waypoints", wps);
        }

        log.info("Driving route request: points={}, waypoints={}", allPoints.size(), waypointList.size());
        return callTencentRouteApi(toEncodedUri(builder), false);
    }

    private Map<String, Object> getMultiSegmentRoute(List<String> allPoints, String mode, String city) {
        String apiPath = routeApiPath(mode);
        List<Map<String, Object>> segmentResults = new ArrayList<>();
        double totalDistance = 0;
        double totalDuration = 0;

        for (int i = 0; i < allPoints.size() - 1; i++) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + apiPath)
                    .queryParam("from", toTMapPoint(allPoints.get(i)))
                    .queryParam("to", toTMapPoint(allPoints.get(i + 1)))
                    .queryParam("key", tmapKey)
                    .queryParam("output", "json");

            if ("transit".equals(mode) && city != null && !city.isEmpty()) {
                builder.queryParam("city", city);
                builder.queryParam("cityd", city);
            }

            String segUrl = toEncodedUri(builder);
            log.info("Segment route request: segment={}, mode={}", i + 1, mode);

            try {
                Map<String, Object> resp = restTemplate.getForObject(segUrl, Map.class);
                if (!isTencentSuccess(resp)) {
                    log.warn("Segment route failed: segment={}, status={}, message={}",
                            i + 1, resp != null ? resp.get("status") : "null", tencentMessage(resp));
                    continue;
                }

                Map<String, Object> result = castMap(resp.get("result"));
                List<Map<String, Object>> routes = result == null ? null : castList(result.get("routes"));
                if (routes == null || routes.isEmpty()) {
                    log.warn("Segment route has no result: segment={}, mode={}", i + 1, mode);
                    continue;
                }

                Map<String, Object> route = routes.get(0);
                List<double[]> decodedPoints = decodeTencentPolyline(castList(route.getOrDefault("polyline", Collections.emptyList())));
                if (decodedPoints.size() < 2) {
                    log.warn("Segment route polyline is invalid: segment={}, mode={}", i + 1, mode);
                    continue;
                }

                double distance = numberToDouble(route.get("distance"));
                double duration = numberToDouble(route.get("duration"));

                Map<String, Object> segData = new LinkedHashMap<>();
                segData.put("points", decodedPoints);
                segData.put("steps", parseSteps(castList(route.get("steps"))));
                segData.put("distance", distance);
                segData.put("duration", duration);
                segmentResults.add(segData);

                totalDistance += distance;
                totalDuration += duration;
                log.info("Segment route completed: segment={}, distance={}m, duration={}min, points={}",
                        i + 1, distance, duration, decodedPoints.size());
            } catch (Exception e) {
                log.warn("Segment route exception: segment={}, mode={}, error={}", i + 1, mode, e.getMessage());
            }
        }

        if (segmentResults.isEmpty()) {
            return emptyRoute("all_segments_failed", "All route segments failed");
        }

        Map<String, Object> mergedPath = mergeSegments(segmentResults, totalDistance, totalDuration);
        log.info("Segment route merged: totalDistance={}m, totalDuration={}min", totalDistance, totalDuration);
        return Collections.singletonMap("paths", Collections.singletonList(mergedPath));
    }

    private String routeApiPath(String mode) {
        switch (mode) {
            case "walk":
                return "/ws/direction/v1/walking/";
            case "transit":
                return "/ws/direction/v1/transit/";
            case "bike":
                return "/ws/direction/v1/bicycling/";
            case "ebike":
                return "/ws/direction/v1/ebicycling/";
            default:
                return "/ws/direction/v1/walking/";
        }
    }

    private Map<String, Object> mergeSegments(List<Map<String, Object>> segments,
                                               double totalDistance,
                                               double totalDuration) {
        List<double[]> allPoints = new ArrayList<>();
        List<Map<String, Object>> allSteps = new ArrayList<>();
        int pointOffset = 0;

        for (int s = 0; s < segments.size(); s++) {
            Map<String, Object> seg = segments.get(s);
            List<double[]> segPoints = castList(seg.get("points"));
            List<Map<String, Object>> segSteps = castList(seg.get("steps"));
            if (segPoints == null || segPoints.isEmpty()) {
                continue;
            }

            int stepOffset = pointOffset;
            if (s == 0) {
                allPoints.addAll(segPoints);
            } else if (segPoints.size() > 1) {
                allPoints.addAll(segPoints.subList(1, segPoints.size()));
                stepOffset = Math.max(0, pointOffset - 1);
            }

            if (segSteps != null) {
                for (Map<String, Object> step : segSteps) {
                    List<Number> idx = castList(step.get("polyline_idx"));
                    if (idx != null && idx.size() == 2) {
                        int newStart = idx.get(0).intValue() + stepOffset;
                        int newEnd = idx.get(1).intValue() + stepOffset;
                        step.put("polyline_idx", Arrays.asList(Math.max(0, newStart), Math.max(0, newEnd)));
                    }
                    allSteps.add(step);
                }
            }

            pointOffset = allPoints.size();
        }

        Map<String, Object> path = new LinkedHashMap<>();
        path.put("distance", totalDistance);
        path.put("duration", Math.round(totalDuration));
        path.put("polyline", allPoints);
        path.put("polyline_decoded", true);
        path.put("steps", allSteps);
        return path;
    }

    private List<double[]> decodeTencentPolyline(List<Number> encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.size() < 2) {
            return points;
        }

        double prevLat = encoded.get(0).doubleValue();
        double prevLng = encoded.get(1).doubleValue();
        if (Math.abs(prevLat) > 90 || Math.abs(prevLng) > 180) {
            prevLat = prevLat / 1000000.0;
            prevLng = prevLng / 1000000.0;
        }
        points.add(new double[]{prevLat, prevLng});

        for (int i = 2; i + 1 < encoded.size(); i += 2) {
            prevLat += encoded.get(i).doubleValue() / 1000000.0;
            prevLng += encoded.get(i + 1).doubleValue() / 1000000.0;
            points.add(new double[]{prevLat, prevLng});
        }

        return points;
    }

    private List<Map<String, Object>> parseSteps(List<Map<String, Object>> rawSteps) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (rawSteps == null) {
            return steps;
        }

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

    private Map<String, Object> callTencentRouteApi(String url, boolean polylineDecoded) {
        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            log.info("Tencent route response status={}", resp != null ? resp.get("status") : "null");

            if (!isTencentSuccess(resp)) {
                String message = tencentMessage(resp);
                log.warn("Tencent route failed: {}", message);
                return emptyRoute("tencent_route_failed", message);
            }

            Map<String, Object> result = castMap(resp.get("result"));
            List<Map<String, Object>> routes = result == null ? null : castList(result.get("routes"));
            List<Map<String, Object>> paths = new ArrayList<>();
            if (routes != null) {
                for (Map<String, Object> route : routes) {
                    List<Number> polyline = castList(route.getOrDefault("polyline", Collections.emptyList()));
                    if (polyline == null || polyline.size() < 2) {
                        continue;
                    }
                    Map<String, Object> path = new LinkedHashMap<>();
                    path.put("distance", route.getOrDefault("distance", 0));
                    path.put("duration", route.getOrDefault("duration", 0));
                    path.put("polyline", polyline);
                    path.put("polyline_decoded", polylineDecoded);
                    path.put("steps", parseSteps(castList(route.get("steps"))));
                    paths.add(path);
                }
            }
            log.info("Route parsed: {} paths", paths.size());
            return Collections.singletonMap("paths", paths);
        } catch (Exception e) {
            log.warn("Tencent route exception: {}", e.getMessage());
            return emptyRoute("tencent_route_exception", e.getMessage());
        }
    }

    private List<String> buildRoutePoints(String origin, String destination, String waypoints) {
        List<String> points = new ArrayList<>();
        addNormalizedPoint(points, origin);
        if (waypoints != null && !waypoints.isEmpty()) {
            for (String wp : waypoints.split(";")) {
                addNormalizedPoint(points, wp);
            }
        }
        addNormalizedPoint(points, destination);

        List<String> deduped = new ArrayList<>();
        String previous = null;
        for (String point : points) {
            if (!point.equals(previous)) {
                deduped.add(point);
            }
            previous = point;
        }
        return deduped;
    }

    private void addNormalizedPoint(List<String> points, String rawPoint) {
        String point = normalizeLngLat(rawPoint);
        if (point != null) {
            points.add(point);
        }
    }

    private String normalizeLngLat(String rawPoint) {
        if (rawPoint == null || rawPoint.trim().isEmpty()) {
            return null;
        }
        String[] parts = rawPoint.trim().split(",");
        if (parts.length != 2) {
            log.warn("Invalid route point format: {}", rawPoint);
            return null;
        }
        try {
            double first = Double.parseDouble(parts[0].trim());
            double second = Double.parseDouble(parts[1].trim());
            double lng = first;
            double lat = second;

            if (Math.abs(first) <= 90 && Math.abs(second) > 90) {
                lng = second;
                lat = first;
            }

            if (!isValidLngLat(lng, lat)) {
                log.warn("Invalid route point coordinate: {}", rawPoint);
                return null;
            }
            return lng + "," + lat;
        } catch (NumberFormatException e) {
            log.warn("Invalid route point number: {}", rawPoint);
            return null;
        }
    }

    private boolean isValidLngLat(double lng, double lat) {
        return lng >= -180 && lng <= 180 && lat >= -90 && lat <= 90;
    }

    private String toTMapPoint(String lngLat) {
        String[] parts = lngLat.split(",");
        if (parts.length == 2) {
            return parts[1] + "," + parts[0];
        }
        return lngLat;
    }

    private String normalizeRouteMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            return "drive";
        }
        String routeMode = mode.trim().toLowerCase(Locale.ROOT);
        switch (routeMode) {
            case "drive":
            case "walk":
            case "transit":
            case "bike":
            case "ebike":
                return routeMode;
            default:
                log.warn("Unsupported route mode: {}, fallback to drive", mode);
                return "drive";
        }
    }

    private Map<String, Object> emptyRoute(String reason, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("paths", Collections.emptyList());
        result.put("fallback", true);
        result.put("reason", reason);
        result.put("message", message == null ? "" : message);
        return result;
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
    public Map<String, Object> searchNearbyHotels(double longitude, double latitude, String city, int radius, int pageIndex) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/place/v1/search")
                .queryParam("keyword", "酒店")
                .queryParam("boundary", "nearby(" + latitude + "," + longitude + "," + radius + ")")
                .queryParam("key", tmapKey)
                .queryParam("order_by", "_distance")
                .queryParam("page_index", pageIndex);

        try {
            Map<String, Object> resp = restTemplate.getForObject(toEncodedUri(builder), Map.class);
            if (isTencentSuccess(resp)) {
                return resp;
            }
            throw new RuntimeException("Nearby hotel search failed: " + tencentMessage(resp));
        } catch (Exception e) {
            log.error("Nearby hotel search failed: {}", e.getMessage());
            throw new RuntimeException("Nearby hotel search failed: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getWeather(double latitude, double longitude) {
        String url = UriComponentsBuilder.fromHttpUrl(TMAP_BASE + "/ws/weather/v1/")
                .queryParam("location", latitude + "," + longitude)
                .queryParam("key", tmapKey)
                .encode(StandardCharsets.UTF_8)
                .toUriString();

        try {
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            if (isTencentSuccess(resp)) {
                return castMap(resp.get("result"));
            }
            throw new RuntimeException("Weather query failed: " + tencentMessage(resp));
        } catch (Exception e) {
            log.error("Weather query failed: {}", e.getMessage());
            throw new RuntimeException("Weather query failed: " + e.getMessage());
        }
    }

    private boolean isTencentSuccess(Map<String, Object> resp) {
        return resp != null && Integer.valueOf(0).equals(resp.get("status"));
    }

    private String tencentMessage(Map<String, Object> resp) {
        if (resp == null) {
            return "No response";
        }
        Object message = resp.get("message");
        return message == null ? "Unknown error" : String.valueOf(message);
    }

    private double numberToDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private String toEncodedUri(UriComponentsBuilder builder) {
        return builder.encode(StandardCharsets.UTF_8).toUriString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object value) {
        return value instanceof List ? (List<T>) value : null;
    }
}
