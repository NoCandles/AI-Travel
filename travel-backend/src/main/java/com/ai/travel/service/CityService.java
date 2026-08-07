package com.ai.travel.service;

import com.ai.travel.entity.City;
import com.ai.travel.dto.CityGroupDTO;

import java.util.List;
import java.util.Map;

public interface CityService {

    /** 获取所有城市（按拼音排序） */
    List<City> getAllCities();

    /** 获取热门城市（hot > 0, 按 hot 降序） */
    List<City> getHotCities();

    /** 获取分组城市数据（热门城市 + A-Z字母分组） */
    CityGroupDTO getGroupedCities();

    /** 根据经纬度查找最近的城市 */
    City getNearestCity(double lng, double lat);

    /** 根据城市名称模糊查找城市（支持省名、市名、区名） */
    City findCityByName(String name);

    /** 获取城市坐标（用于前端兜底） */
    Map<String, Object> getCityCoords(String name);
}
