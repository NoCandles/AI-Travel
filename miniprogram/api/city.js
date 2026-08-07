/**
 * 城市相关接口
 */

const { request } = require('../utils/request');

/** 获取所有城市列表 */
function getCities() {
  return request({
    url: '/api/cities/list',
    method: 'GET'
  });
}

/** 获取热门城市列表 */
function getHotCities() {
  return request({
    url: '/api/cities/hot',
    method: 'GET'
  });
}

/** 获取分组城市数据（热门 + A-Z字母分组） */
function getGroupedCities() {
  return request({
    url: '/api/cities/grouped',
    method: 'GET'
  });
}

/** 根据经纬度查找最近城市 */
function getNearestCity(lng, lat) {
  return request({
    url: '/api/cities/nearest',
    method: 'GET',
    data: { lng, lat }
  });
}

module.exports = {
  getCities,
  getHotCities,
  getGroupedCities,
  getNearestCity
};
