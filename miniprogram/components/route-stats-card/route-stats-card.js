/**
 * 路线统计卡片组件
 * 在地图上展示总距离、预计耗时、途经点数量，以及多路线切换入口
 */
Component({
  properties: {
    visible: { type: Boolean, value: false },
    distance: { type: String, value: '0' },
    duration: { type: String, value: '--' },
    pointCount: { type: Number, value: 0 },
    totalPaths: { type: Number, value: 1 },
    currentPathIndex: { type: Number, value: 0 }
  },

  methods: {
    onTapSwitchPath() {
      this.triggerEvent('switchpath');
    }
  }
});
