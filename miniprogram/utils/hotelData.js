/**
 * 酒店数据层 — 统一数据入口
 * ============================================
 *  前端只调后端，数据库和腾讯地图 API 都在后端处理
 *
 *  前端调 GET  /api/hotels/search?lat=&lng=&city=&radius=
 *  后端逻辑：查 MySQL → 没有则调腾讯地图 API → 存库 → 返回
 *
 *  ⚠️ 未来切换数据源：改后端 /api/hotels/search 的实现即可
 *    前端代码无需任何改动
 * ============================================
 */
const { request } = require('./request');
const { formatDistance } = require('./tmap');

/**
 * 搜索附近酒店
 * @param {number} latitude
 * @param {number} longitude
 * @param {string} city
 * @param {number} radius  单位：米
 * @param {string} keyword 搜索关键词，默认"酒店"，传具体景点名如"曾厝垵酒店"可精确定位
 * @returns {Promise<Array>}
 */
function searchHotels(latitude, longitude, city = '', radius = 3000, keyword = '') {
  const params = [];
  if (latitude) params.push(`lat=${latitude}`);
  if (longitude) params.push(`lng=${longitude}`);
  if (city) params.push(`city=${encodeURIComponent(city)}`);
  if (radius) params.push(`radius=${radius}`);
  if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`);

  return request({
    url: `/api/hotels/search?${params.join('&')}`,
    method: 'GET'
  }).then((data) => {
    // 统一格式化供前端展示
    return (data || []).map((h) => ({
      id: h.hotelId || h.id,
      hotelId: h.hotelId || h.id,
      name: h.name,
      title: h.name,
      address: h.address || '',
      tel: h.tel || '',
      latitude: h.latitude || 0,
      longitude: h.longitude || 0,
      distance: h.distance || 0,
      distanceText: formatDistance(h.distance),
      city: h.city || city || '',
      category: h.category || '',
      tags: (typeof h.tags === 'string' ? h.tags.split(',').filter(Boolean) : (h.tags || [])).slice(0, 3),
      source: h.source || 'db'
    }));
  });
}

module.exports = {
  searchHotels
};
