/**
 * 拾路派 - 离线模式本地路线数据
 * 当后端不可用时，用此数据展示示例路线和酒店
 */
const LOCAL_DATA = {
  routes: {
    '成都': {
      seasonal: { distance: '32km', spotCount: 10,
        days: [{ spots: '成都东站 → 宽窄巷子 → 人民公园鹤鸣茶社 → 奎星楼街 → 太古里夜景' }, { spots: '大熊猫基地 → 文殊院 → 锦里古街 → 武侯祠 → 返程' }] },
      trendy: { distance: '28km', spotCount: 9,
        days: [{ spots: '太古里网红墙 → IFS大熊猫 → 望平街 → 九眼桥酒吧街' }, { spots: 'COSMO成都 → 东郊记忆 → 建设巷小吃 → 返程' }] },
      classic: { distance: '38km', spotCount: 11,
        days: [{ spots: '宽窄巷子 → 武侯祠 → 锦里 → 杜甫草堂 → 青羊宫' }, { spots: '大熊猫基地 → 文殊院 → 春熙路 → 天府广场 → 返程' }] }
    },
    '厦门': {
      seasonal: { distance: '42km', spotCount: 12,
        days: [{ spots: '厦门站 → 鼓浪屿日光岩 → 菽庄花园 → 钢琴博物馆 → 龙头路美食街' }, { spots: '环岛路 → 曾厝垵文创村 → 南普陀寺 → 沙坡尾 → 中山路步行街' }, { spots: '植物园 → 铁路文化公园 → 返程' }] },
      trendy: { distance: '35km', spotCount: 10,
        days: [{ spots: '沙坡尾艺术区 → 猫街 → 顶澳仔 → 演武大桥 → 曾厝垵夜市' }, { spots: '万石植物园仙人掌区 → 钟鼓索道 → 白城沙滩 → 八市海鲜' }, { spots: '鼓浪屿最美转角 → 虫洞书店 → 返程' }] },
      classic: { distance: '40km', spotCount: 11,
        days: [{ spots: '鼓浪屿日光岩 → 菽庄花园 → 皓月园 → 龙头路商业街' }, { spots: '南普陀寺 → 厦门大学 → 环岛路 → 胡里山炮台 → 中山路' }, { spots: '集美学村 → 鳌园 → 陈嘉庚故居 → 返程' }] }
    },
    '北京': {
      seasonal: { distance: '55km', spotCount: 14,
        days: [{ spots: '天安门广场 → 故宫博物院 → 景山公园 → 南锣鼓巷 → 簋街' }, { spots: '八达岭长城 → 明十三陵 → 鸟巢水立方夜景' }, { spots: '颐和园 → 圆明园 → 清华大学 → 三里屯' }, { spots: '天坛公园 → 798艺术区 → 返程' }] },
      trendy: { distance: '40km', spotCount: 11,
        days: [{ spots: '故宫角楼 → 杨梅竹斜街 → pageone书店 → 前门大街' }, { spots: '798艺术区 → 751D·PARK → 三里屯太古里 → 国贸夜景' }, { spots: '环球影城全日' }, { spots: '五道营胡同 → 鼓楼东大街 → 返程' }] },
      classic: { distance: '60km', spotCount: 16,
        days: [{ spots: '天安门升旗 → 故宫 → 天坛 → 前门大栅栏' }, { spots: '八达岭长城 → 定陵 → 奥体中心' }, { spots: '颐和园 → 圆明园 → 北京大学' }, { spots: '雍和宫 → 国子监 → 恭王府 → 返程' }] }
    }
  },
  hotels: {
    '成都': [
      { name: '太古里博舍酒店', meta: '太古里商圈 · 评分4.9', price: '¥1,280' },
      { name: '宽窄巷子钓鱼台', meta: '宽窄巷子旁 · 评分4.7', price: '¥880' }
    ],
    '厦门': [
      { name: '鼓浪屿林氏府', meta: '距码头500m · 评分4.8', price: '¥680' },
      { name: '海景亚朵酒店', meta: '环岛路海景 · 评分4.6', price: '¥420' }
    ],
    '北京': [
      { name: '璞瑄酒店', meta: '王府井商圈 · 评分4.9', price: '¥1,580' },
      { name: '南锣鼓巷如家', meta: '胡同深处 · 评分4.5', price: '¥360' }
    ]
  }
};

const ROUTE_TITLES = { seasonal: '当月时令定制版', trendy: '当下网红爆款版', classic: '经典稳妥版' };

module.exports = { LOCAL_DATA, ROUTE_TITLES };
