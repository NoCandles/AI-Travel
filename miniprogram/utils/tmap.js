/**
 * 腾讯地图工具函数（通过后端代理调用）
 */
const { request } = require('./request');
const mapUtils = require('./map-utils');

/**
 * 逆地理编码（坐标转地址）
 */
function reverseGeocode(longitude, latitude) {
  return request({
    url: `/api/map/reverse-geocode?longitude=${longitude}&latitude=${latitude}`,
    method: 'GET'
  });
}

/**
 * 路径规划
 * @param {string} city 城市名（公交模式时传，用于 transit API）
 */
function getRoute(origin, destination, waypoints = [], type = 'drive', city = '') {
  const wpStr = Array.isArray(waypoints) ? waypoints.join(';') : (waypoints || '');
  let url = `/api/map/route?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&waypoints=${encodeURIComponent(wpStr)}&mode=${type}`;
  if (city) {
    url += `&city=${encodeURIComponent(city)}`;
  }
  return request({
    url: url,
    method: 'GET'
  });
}

/**
 * 地理编码（地址转坐标）
 */
function geocode(address, city = '') {
  return request({
    url: `/api/map/geocode?address=${encodeURIComponent(address)}&city=${encodeURIComponent(city)}`,
    method: 'GET'
  });
}

/**
 * POI 搜索
 */
function searchPOI(keywords, city = '', type = '') {
  return request({
    url: `/api/map/search-poi?keyword=${encodeURIComponent(keywords)}&city=${encodeURIComponent(city)}`,
    method: 'GET'
  }).then(data => {
    // 后端返回 { pois: [...] }
    return (data && data.pois) ? data.pois : [];
  });
}

/**
 * 搜索附近的酒店（直接调用腾讯地图 POI 搜索）
 * @param {number} latitude  纬度
 * @param {number} longitude 经度
 * @param {string} city      城市名（坐标无效时降级用）
 * @param {number} radius    搜索半径（米），默认 3000
 * @param {number} pageIndex 页码
 * @returns {Promise<Array>} 酒店列表
 */
function searchNearbyHotels(latitude, longitude, city = '', radius = 3000, pageIndex = 1) {
  let boundary = '';

  if (latitude && longitude) {
    boundary = `nearby(${latitude},${longitude},${radius})`;
  } else if (city) {
    boundary = `region(${city},0)`;
  } else {
    return Promise.reject(new Error('缺少位置信息'));
  }

  return request({
    url: '/api/map/hotel-search',
    method: 'GET',
    data: {
      keyword: '酒店',
      boundary: boundary,
      order_by: '_distance',
      page_size: 20,
      page_index: pageIndex,
      filter: 'category=酒店'
    }
  });
}

/**
 * 获取酒店距离文字描述
 */
function formatDistance(meters) {
  if (!meters && meters !== 0) return '';
  const m = Number(meters);
  if (m < 1000) return `${Math.round(m)}m`;
  return `${(m / 1000).toFixed(1)}km`;
}

/**
 * 计算两点间距离（Haversine 公式 - 纯前端计算，无需后端）
 */
function calculateDistance(lng1, lat1, lng2, lat2) {
  return mapUtils.calculateDistance(lng1, lat1, lng2, lat2);
}

module.exports = {
  getRoute,
  geocode,
  reverseGeocode,
  searchPOI,
  searchNearbyHotels,
  formatDistance,
  calculateDistance
};
