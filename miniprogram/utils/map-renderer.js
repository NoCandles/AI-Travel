/**
 * 地图渲染工具 — 消除 buildMarkers/loadRealRoute/renderPath 在多个页面的重复
 *
 * 用法：
 *   const { buildMarkers, decodePolyline } = require('../../utils/map-renderer');
 *   const markers = buildMarkers(points, getMarkerIcon);
 */

/**
 * 根据途径点列表构建地图标记
 * @param {Array} points - 途径点数组 [{ latitude, longitude, type? }]
 * @param {Function} getMarkerIcon - 根据 type 返回 icon 路径的函数
 * @returns {Array} markers 数组
 */
function buildMarkers(points, getMarkerIcon) {
  const defaultIcon = '/images/icons/location.svg';
  return points.map((p, idx) => {
    const isStart = idx === 0;
    const isEnd = idx === points.length - 1;
    const label = isStart ? '起' : (isEnd ? '终' : String(idx));
    const bgColor = isStart ? '#52c41a' : (isEnd ? '#f5222d' : '#3A7BD5');

    return {
      id: idx + 1,
      latitude: p.latitude,
      longitude: p.longitude,
      iconPath: getMarkerIcon ? getMarkerIcon(p.type) : defaultIcon,
      width: 28,
      height: 36,
      label: {
        content: label,
        color: '#fff',
        fontSize: 10,
        bgColor,
        padding: 2,
        borderRadius: 8,
        textAlign: 'center'
      }
    };
  });
}

/**
 * 解码腾讯地图 polyline（差分编码 → 坐标数组）
 */
function decodePolyline(encoded) {
  if (!encoded) return [];
  const points = [];
  let lat = 0, lng = 0;
  for (let i = 0; i < encoded.length;) {
    let b, shift = 0, result = 0;
    do { b = encoded.charCodeAt(i++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
    const dlat = (result & 1) ? ~(result >> 1) : (result >> 1);
    lat += dlat;
    shift = 0; result = 0;
    do { b = encoded.charCodeAt(i++) - 63; result |= (b & 0x1f) << shift; shift += 5; } while (b >= 0x20);
    const dlng = (result & 1) ? ~(result >> 1) : (result >> 1);
    lng += dlng;
    points.push({ latitude: lat / 1e5, longitude: lng / 1e5 });
  }
  return points;
}

/**
 * 根据出行方式获取折线样式
 */
function getPolylineStyle(mode) {
  const styles = {
    drive:    { color: '#3A7BD5', width: 6, arrowLine: true, borderColor: '#2c5aa0', borderWidth: 1 },
    walk:     { color: '#52c41a', width: 5, dottedLine: false },
    bicycle:  { color: '#fa8c16', width: 5, dottedLine: false },
    transit:  { color: '#722ed1', width: 5, dottedLine: true, borderColor: '#531dab', borderWidth: 1 }
  };
  return styles[mode] || styles.drive;
}

/**
 * 格式化时长（秒 → "X小时Y分钟"）
 */
function formatDuration(seconds) {
  if (!seconds) return '';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return h + '小时' + (m > 0 ? m + '分' : '');
  return m + '分钟';
}

module.exports = {
  buildMarkers,
  decodePolyline,
  getPolylineStyle,
  formatDuration
};
