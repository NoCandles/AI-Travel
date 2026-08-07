const { request } = require('../utils/request');

/** 获取某个徒步行程下的多条 AI 路线方案。 */
function getRoutesByTrip(tripId) {
  return request({ url: '/api/hiking/trip/' + encodeURIComponent(tripId), method: 'GET' });
}

/** 获取一条徒步路线及其结构化路段。 */
function getRouteDetail(routeId) {
  return request({ url: '/api/hiking/routes/' + encodeURIComponent(routeId), method: 'GET' });
}

function getRouteSegments(routeId) {
  return request({ url: '/api/hiking/routes/' + encodeURIComponent(routeId) + '/segments', method: 'GET' });
}

module.exports = { getRoutesByTrip, getRouteDetail, getRouteSegments };
