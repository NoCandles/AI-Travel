/**
 * 行程出行方式统一映射。
 * 后端的 hiking 与腾讯地图的 walk 不是同一个枚举，必须在进入地图前归一化。
 */
const MODE_META = {
  drive: { key: 'drive', label: '自驾', iconPath: '/images/icons/car.svg' },
  walk: { key: 'walk', label: '徒步', iconPath: '/images/icons/footprints.svg' },
  transit: { key: 'transit', label: '公交出行', iconPath: '/images/icons/bus.svg' },
  bike: { key: 'bike', label: '骑行', iconPath: '/images/icons/transport.svg' },
  ebike: { key: 'ebike', label: '电动车', iconPath: '/images/icons/transport.svg' }
};

function normalizeTravelMode(mode) {
  const value = String(mode || '').toLowerCase();
  if (value === 'hiking' || value === 'walk' || value === 'walking') return 'walk';
  if (value === 'transit' || value === 'bus' || value === 'public_transit') return 'transit';
  if (value === 'bike' || value === 'bicycle' || value === 'cycling') return 'bike';
  if (value === 'ebike' || value === 'electric_bike') return 'ebike';
  return 'drive';
}

function getTravelModeMeta(mode) {
  return MODE_META[normalizeTravelMode(mode)];
}

module.exports = { normalizeTravelMode, getTravelModeMeta };
