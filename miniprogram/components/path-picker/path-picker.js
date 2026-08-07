/**
 * 路线方案选择器组件
 * 在地图上切换不同的路线方案（推荐/方案二/方案三）
 */
Component({
  properties: {
    visible: { type: Boolean, value: false },
    paths: { type: Array, value: [] },
    currentIndex: { type: Number, value: 0 }
  },

  methods: {
    onTapOverlay() {
      this.triggerEvent('close');
    },

    onSelect(e) {
      const index = e.currentTarget.dataset.index;
      this.triggerEvent('select', { index });
    }
  }
});
