/**
 * 途经点时间线组件
 * 以纵向时间线形式展示行程途经点列表，支持编辑模式（拖动/删除）
 * 使用方式：
 *   <waypoint-timeline points="{{points}}" editMode="{{false}}" bind:tap="onPointTap" />
 */
Component({
  properties: {
    points: { type: Array, value: [] },
    editMode: { type: Boolean, value: false },
    itemClasses: { type: Array, value: [] }
  },

  methods: {
    onTapPoint(e) {
      const index = e.currentTarget.dataset.index;
      this.triggerEvent('tap', { index });
    },

    onMoveUp(e) {
      const index = e.currentTarget.dataset.index;
      this.triggerEvent('moveup', { index });
    },

    onMoveDown(e) {
      const index = e.currentTarget.dataset.index;
      this.triggerEvent('movedown', { index });
    },

    onRemove(e) {
      const index = e.currentTarget.dataset.index;
      this.triggerEvent('remove', { index });
    }
  }
});
