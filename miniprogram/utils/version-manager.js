/**
 * 行程版本管理 Behavior
 * 消除 index.js 和 planner.js 中的重复代码
 *
 * 用法：
 *   Page({ behaviors: [versionManager], ... })
 *   然后在页面中可调用 this.showVersionPanel(tripId) 等方法
 */
const api = require('./api');

module.exports = Behavior({
  data: {
    versionPanelVisible: false,
    versionList: [],
    versionLoading: false
  },

  methods: {
    async showVersionPanel(tripId) {
      this.setData({ versionLoading: true, versionPanelVisible: true });
      try {
        const versions = await api.getTripVersions(tripId);
        this.setData({ versionList: versions || [], versionLoading: false });
      } catch (e) {
        wx.showToast({ title: '加载版本失败', icon: 'none' });
        this.setData({ versionLoading: false });
      }
    },

    closeVersionPanel() {
      this.setData({ versionPanelVisible: false });
    },

    async saveCurrentVersion(tripId, data) {
      try {
        await api.createVersion(tripId, data);
        wx.showToast({ title: '版本已保存', icon: 'success' });
      } catch (e) {
        wx.showToast({ title: '保存失败', icon: 'none' });
      }
    },

    async restoreVersion(tripId, versionId) {
      try {
        const data = await api.getVersion(tripId, versionId);
        if (this.loadTripData) {
          this.loadTripData(data);
        }
        this.closeVersionPanel();
        wx.showToast({ title: '版本已恢复', icon: 'success' });
      } catch (e) {
        wx.showToast({ title: '恢复失败', icon: 'none' });
      }
    }
  }
});
