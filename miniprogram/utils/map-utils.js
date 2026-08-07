/**
 * 地图工具函数模块
 * 提取自 trip-detail.js 和 map.js 的共享地图渲染逻辑
 */
var C = require('../config/constants');

const tmap = require('./tmap');

/**
 * 构建地图 markers
 * @param {Array} points - [{ id, name, latitude, longitude, type }, ...]
 * @param {Object} opts - { startColor, endColor, spotColor }
 */
function buildMapMarkers(points, opts) {
  opts = opts || {};
  return points.map(function (p, idx) {
    var label = idx === 0 ? '起' : (idx === points.length - 1 ? '终' : String(idx));
    return {
      id: idx + 1,
      latitude: p.latitude,
      longitude: p.longitude,
      iconPath: getMarkerIcon(p.type),
      width: 28,
      height: 36,
      label: {
        content: label,
        color: '#fff',
        fontSize: 10,
        bgColor: idx === 0 ? (opts.startColor || '#52c41a') : (idx === points.length - 1 ? (opts.endColor || '#f5222d') : (opts.spotColor || '#3A7BD5')),
        padding: 2,
        borderRadius: 8,
        textAlign: 'center'
      }
    };
  });
}

/** Marker 图标路径 */
function getMarkerIcon(type) {
  var icons = {
    start: '/images/marker-start.png',
    end: '/images/marker-end.png',
    spot: '/images/marker-spot.png',
    hotel: '/images/marker-hotel.png'
  };
  return icons[type] || '/images/marker-spot.png';
}

/** 构建直线 polyline（无真实路线数据时使用） */
function buildStraightPolyline(points, color) {
  color = color || '#3A7BD5';
  var polyPoints = points
    .map(function (p) { return { latitude: p.latitude, longitude: p.longitude }; })
    .filter(function (p) { return p.latitude && p.longitude; });
  if (polyPoints.length < 2) return [];
  return [{
    points: polyPoints,
    color: color,
    width: 5,
    arrowLine: true,
    borderColor: '#2463B0',
    borderWidth: 1
  }];
}

/** 渲染真实路线（将后端路径数据转为微信 polyline 格式） */
function renderPath(path, mode, coordPoints) {
  if (!path) return [];
  var style = getPolylineStyle(mode);
  var polylines;

  if (path.steps && path.steps.length > 0 && hasPolylineIdx(path.steps)) {
    polylines = buildSegmentedPolylines(coordPoints, path.steps, style, mode);
  } else {
    polylines = [{
      points: coordPoints,
      color: style.color,
      width: style.width,
      arrowLine: style.arrowLine || false,
      dottedLine: style.dottedLine || false,
      borderColor: style.borderColor || '',
      borderWidth: style.borderWidth || 0
    }];
  }
  return polylines;
}

/** 检查 steps 中是否包含 polyline_idx */
function hasPolylineIdx(steps) {
  for (var i = 0; i < steps.length; i++) {
    var idx = steps[i].polyline_idx;
    if (idx && Array.isArray(idx) && idx.length === 2) return true;
  }
  return false;
}

/** 按 step 分段构建 polyline */
function buildSegmentedPolylines(coordPoints, steps, baseStyle, mode) {
  var polylines = [];
  var roadColors = getRoadColors(mode);
  for (var i = 0; i < steps.length; i++) {
    var step = steps[i];
    var idx = step.polyline_idx;
    if (!idx || !Array.isArray(idx) || idx.length !== 2) continue;
    var startIdx = idx[0], endIdx = idx[1];
    if (startIdx < 0 || endIdx >= coordPoints.length || startIdx >= endIdx) continue;
    var segPoints = coordPoints.slice(startIdx, endIdx + 1);
    if (segPoints.length < 2) continue;
    var cIdx = i % roadColors.length;
    var segColor = roadColors[cIdx];
    var isWalk = step.mode === 'WALKING';
    polylines.push({
      points: segPoints,
      color: segColor,
      width: baseStyle.width || 5,
      arrowLine: !isWalk && (baseStyle.arrowLine || false),
      dottedLine: isWalk || (baseStyle.dottedLine || false),
      borderColor: darkenColor(segColor, 0.3),
      borderWidth: 1
    });
  }
  if (polylines.length === 0) {
    polylines.push({
      points: coordPoints,
      color: baseStyle.color,
      width: baseStyle.width,
      arrowLine: baseStyle.arrowLine || false,
      dottedLine: baseStyle.dottedLine || false
    });
  }
  return polylines;
}

/** 路线颜色方案 */
function getRoadColors(mode) {
  var schemes = {
    drive: ['#3A7BD5', '#4A8FE7', '#5BA3F9', '#2E6BC0', '#1D5AAB'],
    walk: ['#52C41A', '#73D13D', '#95DE64', '#389E0D', '#237804'],
    transit: ['#FA8C16', '#FFA940', '#FFC069', '#D46B08', '#AD4E00'],
    bike: ['#1890FF', '#40A9FF', '#69C0FF', '#096DD9', '#0050B3']
  };
  return schemes[mode] || schemes.drive;
}

/** 加深颜色 */
function darkenColor(hex, factor) {
  hex = hex.replace('#', '');
  var r = Math.floor(parseInt(hex.substring(0, 2), 16) * (1 - factor));
  var g = Math.floor(parseInt(hex.substring(2, 4), 16) * (1 - factor));
  var b = Math.floor(parseInt(hex.substring(4, 6), 16) * (1 - factor));
  return '#' + [r, g, b].map(function (c) {
    return ('0' + Math.max(0, Math.min(255, c)).toString(16)).slice(-2);
  }).join('');
}

/** 路线标签（推荐/方案二/方案三） */
function getPathLabel(index, path, total) {
  if (total <= 1) return '';
  var labels = ['推荐', '方案二', '方案三'];
  return (labels[index] || ('方案' + (index + 1))) + ' ' +
    (path.distance / 1000).toFixed(1) + 'km ' +
    Math.round(path.duration || 0) + '分钟';
}

/** Polyline 样式（按出行方式） */
function getPolylineStyle(mode) {
  var styles = {
    drive: { color: '#3A7BD5', width: 6, arrowLine: true, borderColor: '#2463B0', borderWidth: 1 },
    walk: { color: '#52C41A', width: 5, dottedLine: true, arrowLine: true, borderColor: '#389E0D', borderWidth: 1 },
    transit: { color: '#FA8C16', width: 5, dottedLine: true, arrowLine: true, borderColor: '#D46B08', borderWidth: 1 },
    bike: { color: '#1890FF', width: 5, dottedLine: true, arrowLine: true, borderColor: '#096DD9', borderWidth: 1 }
  };
  return styles[mode] || styles.drive;
}

/**
 * 转换解码后的坐标数组
 * 支持 [[lat, lng], ...] 和 [{latitude, longitude}, ...] 两种格式
 */
function convertDecodedPolyline(decoded) {
  if (!decoded || !Array.isArray(decoded) || decoded.length === 0) return [];
  return decoded.map(function (item) {
    if (Array.isArray(item) && item.length >= 2) {
      return { latitude: Number(item[0]), longitude: Number(item[1]) };
    }
    if (item.latitude !== undefined && item.longitude !== undefined) {
      return { latitude: Number(item.latitude), longitude: Number(item.longitude) };
    }
    return null;
  }).filter(function (p) { return p !== null; });
}

/** 解码增量编码的路线坐标 */
function decodePolyline(encoded) {
  if (!encoded || !Array.isArray(encoded) || encoded.length < 2) return [];
  var prevLat = Number(encoded[0]);
  var prevLng = Number(encoded[1]);
  if (Math.abs(prevLat) > 90 || Math.abs(prevLng) > 180) {
    prevLat = prevLat / 1000000;
    prevLng = prevLng / 1000000;
  }
  var points = [{ latitude: prevLat, longitude: prevLng }];
  for (var i = 2; i < encoded.length; i += 2) {
    if (encoded[i + 1] === undefined) break;
    var lat = prevLat + (Number(encoded[i]) / 1000000);
    var lng = prevLng + (Number(encoded[i + 1]) / 1000000);
    points.push({ latitude: lat, longitude: lng });
    prevLat = lat;
    prevLng = lng;
  }
  return points.filter(function (p) {
    return isFinite(p.latitude) && isFinite(p.longitude) &&
      Math.abs(p.latitude) <= 90 && Math.abs(p.longitude) <= 180;
  });
}

/** 兜底坐标（调用后端城市表查询） */
function getFallbackCoords(index, total, city) {
  var center = { lat: 30.5728, lng: 104.0668 };
  var spread = (index - (total - 1) / 2) * 0.025;
  return { lat: center.lat + spread * 0.6, lng: center.lng + spread };
}

/** 异步获取城市坐标（调用后端城市表） */
function getFallbackCoordsAsync(index, total, city) {
  return new Promise(function(resolve) {
    if (!city || !city.trim()) {
      resolve(getFallbackCoords(index, total, city));
      return;
    }

    var req = require('./request');
    req.request({
      url: '/api/cities/coords?name=' + encodeURIComponent(city.trim()),
      method: 'GET',
      silent: true
    }).then(function(res) {
      if (res && res.found && res.lat && res.lng) {
        var spread = (index - (total - 1) / 2) * 0.025;
        resolve({
          lat: res.lat + spread * 0.6,
          lng: res.lng + spread
        });
      } else {
        resolve(getFallbackCoords(index, total, city));
      }
    }).catch(function() {
      resolve(getFallbackCoords(index, total, city));
    });
  });
}

/** 估算行程时长 */
function estimateDuration(points) {
  if (!points || points.length < 2) return '--';
  var firstTime = points[0].arrivalTime, lastTime = points[points.length - 1].arrivalTime;
  if (firstTime && lastTime) {
    var f = firstTime.split(':').map(Number), l = lastTime.split(':').map(Number);
    var diff = (l[0] * 60 + l[1]) - (f[0] * 60 + f[1]);
    if (diff > 0) {
      var h = Math.floor(diff / 60), m = diff % 60;
      return h > 0 ? (m > 0 ? h + '小时' + m + '分' : h + '小时') : m + '分钟';
    }
  }
  var stay = 0;
  points.forEach(function (p) { stay += (p.duration > 0 ? Number(p.duration) : 60); });
  var total = stay + Math.max(0, points.length - 1) * 15;
  var hh = Math.floor(total / 60), mm = Math.floor(total % 60);
  return hh > 0 ? (mm > 0 ? hh + '小时' + mm + '分' : hh + '小时') : mm + '分钟';
}

/** 格式化分钟数为中文 */
function formatDuration(minutes) {
  if (!minutes && minutes !== 0) return '';
  var total = Number(minutes), h = Math.floor(total / 60), m = Math.floor(total % 60);
  return h > 0 ? (m > 0 ? h + '小时' + m + '分' : h + '小时') : m + '分钟';
}

/** 地图自适应缩放到所有标注点 */
function fitMapToPoints(mapId, points) {
  var mapCtx = wx.createMapContext(mapId);
  if (!mapCtx || !points || points.length === 0) return;
  mapCtx.includePoints({
    points: points.map(function (p) {
      return { latitude: p.latitude, longitude: p.longitude };
    }),
    padding: [80, 60, 80, 60]
  });
}

/** 打开高德地图导航（通过 WebView 中转） */
function openAmapNavigation(points, travelMode, navHost) {
  var modeMap = { drive: 'car', walk: 'walk', transit: 'bus', bike: 'bike', ebike: 'ebike' };
  var mode = modeMap[travelMode] || 'car';
  var start = points[0], end = points[points.length - 1], viaPoints = points.slice(1, -1);
  var host = navHost || C.NAV_BRIDGE_HOST;
  var params = [
    'from=' + encodeURIComponent(start.longitude + ',' + start.latitude),
    'to=' + encodeURIComponent(end.longitude + ',' + end.latitude),
    'mode=' + encodeURIComponent(mode),
    'fromName=' + encodeURIComponent(start.name || '起点'),
    'toName=' + encodeURIComponent(end.name || '终点')
  ];
  if (viaPoints.length > 0) {
    params.push('via=' + encodeURIComponent(viaPoints.map(function (p) { return p.longitude + ',' + p.latitude; }).join(';')));
    params.push('viaNames=' + encodeURIComponent(viaPoints.map(function (p) { return p.name || ''; }).join('|')));
  }
  wx.navigateTo({
    url: '/pages/webview/webview?url=' + encodeURIComponent(host + '/navigation/amap?' + params.join('&'))
  });
}

/** 打开腾讯地图导航（通过 route-plan 插件） */
function openTencentNavigation(points, travelMode, tmapKey) {
  var modeMap = { drive: 'driving', walk: 'walking', transit: 'transit', bike: 'bicycling', ebike: 'ebicycling' };
  var mode = modeMap[travelMode] || 'driving';
  var startJSON = JSON.stringify({ name: points[0].name, latitude: points[0].latitude, longitude: points[0].longitude });
  var endJSON = JSON.stringify({ name: points[points.length - 1].name, latitude: points[points.length - 1].latitude, longitude: points[points.length - 1].longitude });
  var url = 'plugin://route-plan/index?key=' + tmapKey + '&referer=拾路派&startPoint=' + startJSON + '&endPoint=' + endJSON + '&mode=' + mode;
  var viaPoints = points.slice(1, -1);
  if (viaPoints.length > 0) {
    var wps = viaPoints.map(function (p) { return JSON.stringify({ name: p.name, latitude: p.latitude, longitude: p.longitude }); });
    url += '&waypoints=' + encodeURIComponent('[' + wps.join(',') + ']');
  }
  wx.navigateTo({ url: url });
}

/** Haversine 公式计算两点间距离（米） */
function calculateDistance(lng1, lat1, lng2, lat2) {
  const radLat1 = lat1 * Math.PI / 180;
  const radLat2 = lat2 * Math.PI / 180;
  const a = radLat1 - radLat2;
  const b = (lng1 * Math.PI / 180) - (lng2 * Math.PI / 180);
  var s = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2) +
    Math.cos(radLat1) * Math.cos(radLat2) * Math.pow(Math.sin(b / 2), 2)));
  s = s * 6378137;
  return Math.round(s);
}

module.exports = {
  buildMapMarkers,
  getMarkerIcon,
  buildStraightPolyline,
  renderPath,
  hasPolylineIdx,
  buildSegmentedPolylines,
  getRoadColors,
  darkenColor,
  getPathLabel,
  getPolylineStyle,
  convertDecodedPolyline,
  decodePolyline,
  getFallbackCoords,
  getFallbackCoordsAsync,
  estimateDuration,
  formatDuration,
  fitMapToPoints,
  openAmapNavigation,
  openTencentNavigation,
  calculateDistance
};
