package com.ai.travel.dto;

import lombok.Data;

import java.util.List;

/**
 * 城市分组查询响应
 */
@Data
public class CityGroupDTO {

    /** 热门城市列表 */
    private List<CityItem> hotCities;

    /** A-Z 字母分组 */
    private List<CityLetterGroup> groupCities;

    /** 城市总数 */
    private Integer totalCount;

    @Data
    public static class CityItem {
        private String id;
        private String name;
        private String pinyin;

        public CityItem(String id, String name, String pinyin) {
            this.id = id;
            this.name = name;
            this.pinyin = pinyin;
        }
    }

    @Data
    public static class CityLetterGroup {
        private String letter;
        private List<CityItem> cities;
    }
}
