const { request } = require('../utils/request');

/** 创建行程 */
function createTrip(tripData) {
  return request({
    url: '/api/trips',
    method: 'POST',
    data: tripData
  });
}

/** 获取行程列表 */
function getTripList() {
  return request({
    url: '/api/trips',
    method: 'GET'
  });
}

/** 获取行程数量 */
function getTripCount() {
  return request({
    url: '/api/trips/count',
    method: 'GET'
  });
}

/** 获取行程详情 */
function getTripDetail(tripId) {
  return request({
    url: '/api/trips/' + tripId,
    method: 'GET'
  });
}

/** 更新行程 */
function updateTrip(tripId, tripData) {
  return request({
    url: '/api/trips/' + tripId,
    method: 'PUT',
    data: tripData
  });
}

/** 删除行程 */
function deleteTrip(tripId) {
  return request({
    url: '/api/trips/' + tripId,
    method: 'DELETE'
  });
}

/** 保存行程 */
function saveTrip(tripId) {
  return request({
    url: '/api/trips/' + tripId + '/save',
    method: 'PUT'
  });
}

/** 重新生成行程 */
function regenerateTrip(tripId) {
  return request({
    url: '/api/trips/' + tripId + '/regenerate',
    method: 'POST'
  });
}

/** 重新生成单天 */
function regenerateDay(tripId, dayNum) {
  return request({
    url: '/api/trips/' + tripId + '/days/' + dayNum + '/regenerate',
    method: 'POST'
  });
}

/** 精准替换单个景点（不动同天其它景点） */
function replaceSpot(tripId, spotId, hint) {
  return request({
    url: '/api/trips/' + tripId + '/spots/' + spotId + '/replace',
    method: 'POST',
    data: { hint: hint || '' }
  });
}

/** 保存行程版本 */
function saveTripVersion(tripId, note) {
  return request({
    url: '/api/trips/' + tripId + '/versions',
    method: 'POST',
    data: { note: note || '手动保存' }
  });
}

/** 获取行程版本列表 */
function getTripVersions(tripId) {
  return request({
    url: '/api/trips/' + tripId + '/versions',
    method: 'GET'
  });
}

/** 恢复行程版本 */
function restoreTripVersion(tripId, versionId) {
  return request({
    url: '/api/trips/' + tripId + '/versions/' + versionId + '/restore',
    method: 'POST'
  });
}

/** 智能推荐目的地 */
function getRecommendedDestinations() {
  return request({
    url: '/api/trips/recommend/destinations',
    method: 'GET'
  });
}

/** 更新行程中的某一天 */
function updateDay(dayId, data) {
  return request({
    url: '/api/trips/days/' + dayId,
    method: 'PUT',
    data: data
  });
}

/** 重新排序景点 */
function reorderSpots(dayId, spotIds) {
  return request({
    url: '/api/trips/days/' + dayId + '/spots/reorder',
    method: 'PUT',
    data: spotIds
  });
}

/** AI 获取行李清单 */
function getPackingList(destination, days, travelMode, weather) {
  // 构建基础 URL，只包含非空参数
  let params = [];
  if (destination && destination.trim() !== '') {
    params.push('destination=' + encodeURIComponent(destination));
  }
  if (days) params.push('days=' + days);
  if (travelMode) params.push('travelMode=' + travelMode);
  if (weather && weather.trim() !== '') {
    params.push('weather=' + encodeURIComponent(weather));
  }
  
  let url = '/api/trips/packing-list';
  if (params.length > 0) {
    url += '?' + params.join('&');
  }
  return request({ url, method: 'GET' });
}

/** 通过行程ID获取已存储的行李清单 */
function getPackingListByTripId(tripId) {
  return request({
    url: '/api/trips/packing-list?tripId=' + encodeURIComponent(tripId),
    method: 'GET'
  });
}

/** 保存行李清单到后端 */
function savePackingList(tripId, categories) {
  return request({
    url: '/api/trips/' + tripId + '/packing-list',
    method: 'PUT',
    data: categories
  });
}

/** 重新生成行李清单 */
function regeneratePackingList(tripId) {
  return request({
    url: '/api/trips/' + tripId + '/packing-list/regenerate',
    method: 'POST'
  });
}

/** 开始行程 */
function startTrip(tripId) {
  return request({ url: '/api/trips/' + tripId + '/start', method: 'POST' });
}

/** 结束行程 */
function completeTrip(tripId) {
  return request({ url: '/api/trips/' + tripId + '/complete', method: 'POST' });
}

/** 获取当前进行中的行程 */
function getOngoingTrip() {
  return request({ url: '/api/trips/ongoing', method: 'GET' });
}

/** 打卡到达景点 */
function checkInSpot(spotId) {
  return request({ url: '/api/trips/spots/' + spotId + '/check-in', method: 'POST' });
}

/** 更新景点信息 */
function updateSpot(spotId, data) {
  return request({
    url: '/api/trips/spots/' + spotId,
    method: 'PUT',
    data: data
  });
}

module.exports = {
  createTrip,
  getTripList,
  getTripCount,
  getTripDetail,
  updateTrip,
  deleteTrip,
  saveTrip,
  regenerateTrip,
  regenerateDay,
  replaceSpot,
  saveTripVersion,
  getTripVersions,
  restoreTripVersion,
  getRecommendedDestinations,
  updateDay,
  reorderSpots,
  getPackingList,
  getPackingListByTripId,
  savePackingList,
  regeneratePackingList,
  startTrip,
  completeTrip,
  getOngoingTrip,
  checkInSpot,
  updateSpot
};
