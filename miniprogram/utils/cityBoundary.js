/**
 * 城市简化边界多边形生成器
 * 根据城市中心坐标 + 近似半径，生成 12 顶点不规则多边形模拟行政区边界
 */

// 各城市近似行政区域半径（km），用于生成多边形大小
const CITY_RADIUS = {
  '重庆': 90, '阿坝': 80, '拉萨': 70, '西宁': 65,
  '成都': 65, '杭州': 60, '哈尔滨': 60, '西安': 60,
  '北京': 55, '上海': 55, '武汉': 55, '长沙': 55,
  '南京': 55, '广州': 55, '深圳': 45, '青岛': 50,
  '大连': 50, '苏州': 50, '桂林': 50, '银川': 50,
  '呼和浩特': 50, '兰州': 50, '昆明': 55,
  '厦门': 40, '三亚': 40, '丽江': 45, '大理': 45,
  '北海': 40, '南宁': 50, '贵阳': 55, '珠海': 35
};

const DEFAULT_RADIUS = 50;

/**
 * 简单伪随机数生成器（基于坐标哈希种子）
 */
function createRng(seed) {
  let s = Math.abs(seed | 0) || 1;
  return function () {
    s = (s * 16807) % 2147483647;
    return (s - 1) / 2147483646;
  };
}

/**
 * 生成单个城市的简化边界多边形
 * @param {Object} city - { name, lng, lat }
 * @param {string} fillColor - 填充颜色（含透明度）
 * @param {string} strokeColor - 边框颜色
 * @param {number} [strokeWidth] - 边框宽度
 * @returns {Object} 微信 map polygons 配置项
 */
function generateCityPolygon(city, fillColor, strokeColor, strokeWidth) {
  var lng = Number(city.lng);
  var lat = Number(city.lat);
  var radius = CITY_RADIUS[city.name] || DEFAULT_RADIUS;

  var rng = createRng(Math.round(lng * 1000) + Math.round(lat * 777));
  var points = [];
  var numVertices = 14;

  for (var i = 0; i < numVertices; i++) {
    var angle = (i / numVertices) * Math.PI * 2;
    // 半径波动 0.55 ~ 1.05，模拟不规则边界
    var rFactor = 0.55 + rng() * 0.50;
    var r = radius * rFactor;

    // 经度方向需按纬度修正
    var dLng = (r / 111.32) / Math.cos(lat * Math.PI / 180);
    var dLat = r / 110.57;

    points.push({
      longitude: +(lng + dLng * Math.cos(angle)).toFixed(6),
      latitude: +(lat + dLat * Math.sin(angle)).toFixed(6)
    });
  }

  return {
    points: points,
    strokeWidth: strokeWidth || 1.5,
    strokeColor: strokeColor,
    fillColor: fillColor,
    zIndex: 1
  };
}

/**
 * 批量生成到访城市的高亮多边形
 * @param {Array} cities - 到访城市列表，每项含 { name, lng, lat, tripCount }
 * @returns {Array} polygons 数组
 */
function generateVisitedPolygons(cities) {
  return cities.map(function (city) {
    return generateCityPolygon(
      city,
      '#2EC4B670',   // 品牌色更深填充
      '#2EC4B6CC',   // 品牌色更明显边框
      2
    );
  });
}

module.exports = {
  generateCityPolygon: generateCityPolygon,
  generateVisitedPolygons: generateVisitedPolygons,
  CITY_RADIUS: CITY_RADIUS
};
