const { request } = require('../utils/request');

/** 更新景点 */
function updateSpot(spotId, data) {
  return request({
    url: '/api/trips/spots/' + spotId,
    method: 'PUT',
    data: data
  });
}

/** 删除景点 */
function deleteSpot(spotId) {
  return request({
    url: '/api/trips/spots/' + spotId,
    method: 'DELETE'
  });
}

/** 添加景点 */
function addSpot(tripId, dayId, data) {
  return request({
    url: '/api/trips/' + tripId + '/days/' + dayId + '/spots',
    method: 'POST',
    data: data
  });
}

module.exports = {
  updateSpot,
  deleteSpot,
  addSpot
};
