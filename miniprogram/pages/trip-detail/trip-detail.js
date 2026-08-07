const api = require('../../api/social.js');
const tripApi = require('../../api/trip.js');
const app = getApp();
const tmap = require('../../utils/tmap');
const mapUtils = require('../../utils/map-utils');
const { normalizeTravelMode, getTravelModeMeta } = require('../../utils/travel-mode');
const cache = require('../../utils/cache');
const {
  applyTheme
} = require('../../utils/theme');
const auth = require('../../utils/auth');

Page({
  data: {
    loading: true,
    darkMode: wx.getStorageSync('darkMode') || false,
    trip: null,
    publishId: '',

    // 评论相关
    comments: [],
    commentExpanded: {},
    commentText: '',
    showReplyInput: false,
    replyToComment: null,
    currentUserId: '',

    // UI 状态
    navPaddingTop: 44,
    activeTab: 'route',
    activeDay: 0,
    itineraryExpanded: true,
    currentDayItem: {},
    allSpots: [],

    // 评论分页
    commentPage: 1,
    commentHasMore: true,
    loadingComments: false,
    markers: [],
    polylines: [],
    mapData: {
      latitude: 30.5728,
      longitude: 104.0668,
      scale: 12
    },
    routeStats: {
      distance: '0',
      duration: '--',
      spots: 0,
      points: []
    },
    totalCost: {
      transport: 0,
      hotel: 0,
      food: 0,
      ticket: 0,
      total: 0
    },

    // 地图相关状态
    travelMode: 'drive',
    travelModeMeta: getTravelModeMeta('drive'),
    travelModes: [{
        key: 'drive',
        label: '驾车',
        iconPath: '/images/icons/car.svg'
      },
      {
        key: 'walk',
        label: '步行',
        iconPath: '/images/icons/footprints.svg'
      },
      {
        key: 'transit',
        label: '公交',
        iconPath: '/images/icons/bus.svg'
      },
      {
        key: 'bike',
        label: '骑行',
        iconPath: '/images/icons/transport.svg'
      }
    ],
    loadingRoute: false,
    routeLoaded: false,
    currentPathIndex: 0,
    totalPaths: 0,
    allPaths: [],
    showPathPicker: false,
    mapDayDate: '',

    // 编辑游玩推荐
    showGuideModal: false,
    editingSpot: null,
    editingGuide: '',

    // 顶部轮播视频
    heroCurrentIndex: 0,
    heroVideoPlaying: -1
  },

  onLoad(options) {
    applyTheme(this);
    this.setNavPadding();

    // 先读取用户信息（loadData 需要 currentUserId）
    const userInfo = wx.getStorageSync('userInfo');
    if (userInfo && userInfo.id) {
      this.setData({
        currentUserId: userInfo.id
      });
    }

    // 兼容 publishId 和 id 两种参数
    const publishId = options.publishId || options.id;
    // 从广场进入时，禁用编辑功能（广场只做展示）
    const fromPlaza = !!options.publishId;
    if (publishId) {
      this.setData({
        publishId: publishId,
        fromPlaza: fromPlaza
      });
      this.loadData(publishId);
    } else {
      console.error('没有传入 publishId/id 参数');
      this.setData({
        loading: false
      });
      wx.showToast({
        title: '参数错误',
        icon: 'none'
      });
    }

    // 启用分享（好友 + 朋友圈）
    wx.showShareMenu({
      withShareTicket: true,
      menus: ['shareAppMessage', 'shareTimeline']
    });
  },

  setNavPadding() {
    try {
      const menu = wx.getMenuButtonBoundingClientRect();
      this.setData({
        navPaddingTop: menu && menu.top ? menu.top : 44
      });
    } catch (e) {
      this.setData({
        navPaddingTop: 44
      });
    }
  },

  onShow() {
    applyTheme(this);
  },

  loadData(publishId) {
    this.setData({
      loading: true,
      commentPage: 1,
      commentHasMore: true
    });

    // 缓存优先：5 分钟 TTL，先展示缓存 → 静默拉取最新
    const CACHE_TTL = 5 * 60 * 1000;
    const cacheKey = 'trip_detail_' + publishId;
    const cached = cache.get(cacheKey, CACHE_TTL);

    if (cached) {
      const builtCache = this.buildTripData(cached);
      const isOwnerCache = !!(cached.userId && this.data.currentUserId && cached.userId === this.data.currentUserId);
      this.setData({
        loading: false,
        trip: builtCache,
        isOwner: isOwnerCache,
        comments: builtCache.comments || [],
        activeDay: 0,
        currentDayItem: builtCache.dayList && builtCache.dayList[0] ? builtCache.dayList[0] : {},
        allSpots: builtCache.spots || [],
        mapData: builtCache.mapData || this.data.mapData,
        routeStats: builtCache.routeStats || this.data.routeStats,
        totalCost: builtCache.totalCost || this.data.totalCost
      }, () => this.loadMapDay(0));
    }

    const userId = this.data.currentUserId || undefined;
    Promise.all([
        api.getPublishDetail(publishId, userId),
        api.getComments(publishId, userId, 1, 10).catch(err => {
          console.warn('[trip-detail] 评论加载失败，不影响行程详情:', err);
          return [];
        })
      ])
      .then(([trip, comments]) => {
        if (!trip) {
          if (!this.data.trip) {
            this.setData({ loading: false, trip: null });
          } else {
            this.setData({ loading: false });
          }
          return;
        }
        trip.comments = comments || [];
        // 缓存原始行程数据（不含 comments，下次进入会重新加载评论）
        var rawForCache = Object.assign({}, trip);
        delete rawForCache.comments;
        cache.set(cacheKey, rawForCache);
        const built = this.buildTripData(trip);
        const travelMode = normalizeTravelMode(built.travelMode);
        // 判断是否是行程拥有者
        const isOwner = !!(trip.userId && this.data.currentUserId && trip.userId === this.data.currentUserId);
        this.setData({
          loading: false,
          trip: built,
          isOwner: isOwner,
          comments: built.comments || [],
          commentHasMore: (comments || []).length >= 10,
          activeDay: 0,
          currentDayItem: built.dayList && built.dayList[0] ? built.dayList[0] : {},
          allSpots: built.spots || [],
          mapData: built.mapData || this.data.mapData,
          routeStats: built.routeStats || this.data.routeStats,
          totalCost: built.totalCost || this.data.totalCost,
          travelMode,
          travelModeMeta: getTravelModeMeta(travelMode)
        }, () => this.loadMapDay(0));
      })
      .catch(err => {
        // 已有缓存数据时，静默失败不弹 toast
        if (!this.data.trip) {
          this.setData({ loading: false });
          wx.showToast({ title: '加载失败', icon: 'none' });
        } else {
          this.setData({ loading: false });
        }
      });
  },

  buildTripData(trip) {
    const copy = {
      ...trip
    };
    copy.likeCount = trip.likeCount || 0;
    copy.commentCount = trip.commentCount || 0;
    copy.favCount = trip.favCount || 0;
    copy.isLiked = trip.isLiked || false;
    copy.isFaved = trip.isFaved || false;
    copy.isFollowed = trip.isFollowed || false;

    if (trip.dayList && trip.dayList.length > 0) {
      copy.dayList = trip.dayList.map(day => ({
        ...day,
        spots: (day.spots || []).map(spot => ({
          ...spot,
          _expanded: false
        }))
      }));
    } else {
      copy.dayList = [{
        date: '',
        spots: []
      }];
    }

    const allSpots = [];
    const dayList = copy.dayList;
    if (dayList) {
      dayList.forEach((day, dayIndex) => {
        if (day.spots) {
          day.spots.forEach((spot, spotIndex) => {
            allSpots.push({
              ...spot,
              dayIndex,
              spotIndex
            });
          });
        }
      });
    }
    copy.spots = allSpots;
    const firstSpot = allSpots[0] || {};
    const firstGalleryImage = trip.images && trip.images[0] ? trip.images[0].imageUrl : '';
    copy.heroImageSrc = trip.coverImage || firstGalleryImage || firstSpot.image || firstSpot.coverImage || '/images/小程序头像.png';

    const mapData = {
      latitude: 30.5728,
      longitude: 104.0668,
      scale: 12
    };
    const routeStats = {
      distance: '0',
      duration: '--',
      spots: allSpots.length,
      points: []
    };
    // 从后端数据中读取费用信息，若不存在则初始化为 0
    const rawCost = trip.cost || trip.totalCost || {};
    const totalCost = {
      transport: rawCost.transport || 0,
      hotel: rawCost.hotel || 0,
      food: rawCost.food || 0,
      ticket: rawCost.ticket || 0,
      total: rawCost.total || 0
    };

    let comments = [];
    const apiComments = trip.comments;
    if (apiComments && apiComments.length) {
      comments = this.mapCommentsData(apiComments);
    }
    copy.comments = comments;

    // ✅ 计算行李清单打包进度
    let packedChecked = 0,
      packedTotal = 0;
    (trip.packingList || []).forEach(cat => {
      (cat.items || []).forEach(item => {
        packedTotal++;
        if (item.checked) packedChecked++;
      });
    });
    copy.packingProgress = {
      checked: packedChecked,
      total: packedTotal,
      percent: packedTotal > 0 ? Math.round(packedChecked / packedTotal * 100) : 0
    };

    return {
      ...copy,
      mapData,
      routeStats,
      totalCost
    };
  },

  // ===== UI 交互 =====

  goBack() {
    wx.navigateBack({
      fail: () => wx.switchTab({
        url: '/pages/index/index'
      })
    });
  },

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab;
    this.setData({
      activeTab: tab
    });
    if (tab === 'route') {
      this.loadMapDay(this.data.activeDay);
    }
  },

  onHeroImageError() {
    const trip = this.data.trip || {};
    const spots = trip.spots || [];
    const fallback = spots.find(item => item.image && item.image !== trip.heroImageSrc);
    this.setData({
      'trip.heroImageSrc': fallback ? fallback.image : '/images/小程序头像.png'
    });
  },

  locateRoute() {
    const points = this.data.routeStats.points || [];
    if (points.length) this.fitMapToPoints(points);
  },

  changeMapScale(e) {
    const delta = Number(e.currentTarget.dataset.delta || 0);
    const nextScale = Math.max(5, Math.min(18, (this.data.mapData.scale || 12) + delta));
    this.setData({ 'mapData.scale': nextScale });
  },

  switchTravelMode(e) {
    const mode = normalizeTravelMode(e.currentTarget.dataset.mode);
    if (!mode || mode === this.data.travelMode) return;
    this.setData({
      travelMode: mode,
      travelModeMeta: getTravelModeMeta(mode)
    }, () => this.loadMapDay(this.data.activeDay));
  },

  switchDay(e) {
    const index = e.currentTarget.dataset.index;
    const dayList = this.data.trip.dayList;
    if (dayList && dayList[index]) {
      this.setData({
        activeDay: index,
        currentDayItem: dayList[index]
      });
    }
  },

  toggleItinerarySheet() {
    this.setData({
      itineraryExpanded: !this.data.itineraryExpanded
    });
  },

  scrollToComment() {
    this.setData({
      activeTab: 'comment'
    });
  },

  // ===== 顶部轮播视频 =====

  onHeroSwiperChange(e) {
    const index = e.detail.current;
    // 切换时暂停之前播放的视频
    if (this.data.heroVideoPlaying !== -1 && this.data.heroVideoPlaying !== index) {
      const prevCtx = wx.createVideoContext('hero-video-' + this.data.heroVideoPlaying, this);
      if (prevCtx) prevCtx.pause();
    }
    this.setData({
      heroCurrentIndex: index,
      heroVideoPlaying: -1
    });
  },

  playHeroVideo(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ heroVideoPlaying: index });
    // 延迟一帧确保 video 组件已渲染 controls
    setTimeout(() => {
      const ctx = wx.createVideoContext('hero-video-' + index, this);
      if (ctx) ctx.play();
    }, 50);
  },

  previewHeroImage(e) {
    const index = Number(e.currentTarget.dataset.index) || 0;
    const images = this.data.trip.images || [];
    // 只收集图片类型，过滤掉视频
    const urls = images
      .filter(item => item.mediaType !== 'video')
      .map(item => item.imageUrl)
      .filter(Boolean);
    if (!urls.length) return;
    // 找到被点击图片在纯图片列表中的位置
    const tapped = images[index];
    const current = (tapped && tapped.mediaType !== 'video') ? tapped.imageUrl : urls[0];
    wx.previewImage({ current, urls });
  },

  // ===== 点赞 / 收藏 / 关注 =====

  toggleLike() {
    if (!auth.checkLogin()) return;
    const trip = this.data.trip;
    if (!trip || !trip.id) return;
    const newLiked = !trip.isLiked;
    const nextLikeCount = Math.max(0, newLiked ? trip.likeCount + 1 : trip.likeCount - 1);
    this.setData({
      'trip.isLiked': newLiked,
      'trip.likeCount': nextLikeCount
    });
    api.toggleLike({
        targetId: trip.id,
        targetType: 'publish'
      })
      .then(() => {
        app.globalData.squareDirty = true;
        app.globalData.tripListDirty = true;
        this.syncPreviousPublishCard(trip.id, { likeCount: nextLikeCount, isLiked: newLiked });
      })
      .catch(() => {
        this.setData({
          'trip.isLiked': !newLiked,
          'trip.likeCount': trip.likeCount
        });
      });
  },

  toggleFav() {
    if (!auth.checkLogin()) return;
    const trip = this.data.trip;
    if (!trip || !trip.id) return;
    const newFaved = !trip.isFaved;
    const nextFavCount = Math.max(0, newFaved ? trip.favCount + 1 : trip.favCount - 1);
    this.setData({
      'trip.isFaved': newFaved,
      'trip.favCount': nextFavCount
    });
    if (newFaved) {
      api.addFav(trip.id)
        .then(() => {
          app.globalData.statsDirty = true;
          app.globalData.squareDirty = true;
          app.globalData.tripListDirty = true;
          this.syncPreviousPublishCard(trip.id, { favCount: nextFavCount, isFaved: newFaved });
        })
        .catch(() => {
          this.setData({
            'trip.isFaved': !newFaved,
            'trip.favCount': trip.favCount
          });
        });
    } else {
      api.removeFav(trip.id)
        .then(() => {
          app.globalData.statsDirty = true;
          app.globalData.squareDirty = true;
          app.globalData.tripListDirty = true;
          this.syncPreviousPublishCard(trip.id, { favCount: nextFavCount, isFaved: newFaved });
        })
        .catch(() => {
          this.setData({
            'trip.isFaved': !newFaved,
            'trip.favCount': trip.favCount
          });
        });
    }
  },

  syncPreviousPublishCard(publishId, patch) {
    const pages = getCurrentPages();
    const prevPage = pages.length > 1 ? pages[pages.length - 2] : null;
    if (!prevPage || !prevPage.data) return;

    ['trips', 'leftTrips', 'rightTrips'].forEach((key) => {
      const list = prevPage.data[key];
      if (!Array.isArray(list)) return;
      const index = list.findIndex(item => item && item.id === publishId);
      if (index >= 0 && prevPage.setData) {
        const update = {};
        Object.keys(patch).forEach(field => {
          update[`${key}[${index}].${field}`] = patch[field];
        });
        prevPage.setData(update);
      }
    });
  },

  /** ✅ 跳转行李清单 —— 传递数据，不再发独立请求 */
  goPackingList() {
    const trip = this.data.trip;
    if (!trip) return;
    // 从行程详情页进入行李清单，只能查看不能编辑
    const canEdit = false;
    // 注意：trip.id 是 TripPublish 的 ID，trip.tripPlanId 才是 TripPlan 的 ID
    const planId = trip.tripPlanId || trip.id || '';
    const destination = trip.location || trip.destination || '';

    // ✅ 将 packingList 数据序列化后传递（零网络请求）
    const packingList = JSON.stringify(trip.packingList || []);

    wx.navigateTo({
      url: '/pages/packing-list/packing-list?' +
        'packingList=' + encodeURIComponent(packingList) +
        '&tripId=' + encodeURIComponent(planId) +
        '&destination=' + encodeURIComponent(destination) +
        '&days=' + (trip.days || 1) +
        '&travelMode=' + (trip.travelMode || 'drive') +
        '&canEdit=' + (canEdit ? '1' : '0')
    });
  },

  toggleFollow() {
    if (!auth.checkLogin()) return;
    const trip = this.data.trip;
    if (!trip || !trip.userId) return;
    const isFollowed = trip.isFollowed;
    this.setData({
      'trip.isFollowed': !isFollowed
    });
    const action = isFollowed ? api.unfollowUser(trip.userId) : api.followUser(trip.userId);
    action.catch(() => this.setData({
      'trip.isFollowed': isFollowed
    }));
  },

  viewAuthorProfile() {
    const trip = this.data.trip;
    if (!trip || !trip.userId) return;
    if (trip.userId === this.data.currentUserId) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({ url: '/pages/userprofile/userprofile?userId=' + trip.userId });
  },

  viewCommentUser(e) {
    const userId = e.currentTarget.dataset.userId;
    if (!userId) return;
    if (userId === this.data.currentUserId) {
      wx.switchTab({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({ url: '/pages/userprofile/userprofile?userId=' + userId });
  },

  shareToChat() {
    if (!auth.checkLogin()) return;
    const publishId = this.data.publishId || (this.data.trip && this.data.trip.id);
    if (!publishId) {
      wx.showToast({ title: '路线不存在', icon: 'none' });
      return;
    }
    app.globalData.pendingShareRouteId = publishId;
    wx.switchTab({ url: '/pages/messages/messages' });
  },

  onShareAppMessage() {
    const trip = this.data.trip;
    if (!trip) return {
      title: '拾路派 - 精彩旅程'
    };
    const title = trip.title || trip.name || (trip.location ? trip.location + '之旅' : '') || (trip.destination ? trip.destination + '之旅' : '') || '精彩行程';
    return {
      title: title,
      imageUrl: trip.coverImage || '',
      path: '/pages/trip-detail/trip-detail?publishId=' + (trip.id || this.data.publishId)
    };
  },

  onShareTimeline() {
    const trip = this.data.trip;
    if (!trip) return {
      title: '拾路派 - 精彩旅程'
    };
    const title = trip.title || trip.name || (trip.location ? trip.location + '之旅' : '') || (trip.destination ? trip.destination + '之旅' : '') || '精彩行程';
    return {
      title: title,
      imageUrl: trip.coverImage || '',
      query: 'publishId=' + (trip.id || this.data.publishId)
    };
  },

  // ===== 评论相关 =====

  loadComments(page) {
    const publishId = this.data.publishId;
    const userId = this.data.currentUserId || undefined;
    if (!publishId) return Promise.resolve([]);

    const pg = page || 1;
    return api.getComments(publishId, userId, pg, 10)
      .then(comments => {
        const commentList = this.mapCommentsData(comments || []);
        if (pg === 1) {
          this.setData({
            comments: commentList,
            commentHasMore: (comments || []).length >= 10
          });
        } else {
          this.setData({
            comments: this.data.comments.concat(commentList),
            commentHasMore: (comments || []).length >= 10
          });
        }
        return commentList;
      })
      .catch(err => {
        console.error('加载评论列表失败:', err);
        return [];
      });
  },

  loadMoreComments() {
    if (this.data.loadingComments || !this.data.commentHasMore) return;
    this.setData({
      loadingComments: true,
      commentPage: this.data.commentPage + 1
    });
    this.loadComments(this.data.commentPage)
      .finally(() => this.setData({
        loadingComments: false
      }));
  },

  mapCommentsData(comments) {
    const trip = this.data.trip;
    return comments.map(c => ({
      id: c.id,
      nickname: c.nickname || '用户',
      avatar: c.avatar || '',
      content: c.content,
      createTime: c.createTime || '刚刚',
      likeCount: c.likeCount || 0,
      isLiked: c.isLiked || false,
      parentId: c.parentId,
      replyToNickname: c.replyToNickname,
      isAuthor: trip && trip.userId && c.userId === trip.userId,
      userId: c.userId,
      replies: (c.replies || []).map(r => ({
        id: r.id,
        nickname: r.nickname || '用户',
        avatar: r.avatar || '',
        content: r.content,
        createTime: r.createTime || '刚刚',
        likeCount: r.likeCount || 0,
        isLiked: r.isLiked || false,
        parentId: r.parentId,
        replyToNickname: r.replyToNickname,
        isAuthor: trip && trip.userId && r.userId === trip.userId,
        userId: r.userId
      }))
    }));
  },

  onCommentInput(e) {
    this.setData({
      commentText: e.detail.value
    });
  },

  replyToComment(e) {
    const comment = e.currentTarget.dataset.comment;
    this.setData({
      showReplyInput: true,
      replyToComment: comment,
      commentText: ''
    });
  },

  cancelReply() {
    this.setData({
      showReplyInput: false,
      replyToComment: null,
      commentText: ''
    });
  },

  toggleCommentExpand(e) {
    const commentId = e.currentTarget.dataset.id;
    const expanded = {
      ...this.data.commentExpanded
    };
    if (expanded[commentId] === false) {
      delete expanded[commentId];
    } else {
      expanded[commentId] = false;
    }
    this.setData({
      commentExpanded: expanded
    });
  },

  submitComment() {
    if (!auth.checkLogin()) return;
    if (!this.data.commentText.trim()) return;
    const publishId = this.data.publishId;
    if (!publishId) {
      wx.showToast({
        title: '无法评论',
        icon: 'none'
      });
      return;
    }

    const apiMethod = this.data.replyToComment ? api.replyComment : api.addComment;
    const requestData = {
      publishId,
      content: this.data.commentText.trim(),
      parentId: this.data.replyToComment ? this.data.replyToComment.id : null
    };

    apiMethod(requestData)
      .then(() => {
        this.setData({
          commentText: '',
          replyToComment: null,
          showReplyInput: false,
          commentPage: 1
        });
        const trip = {
          ...this.data.trip
        };
        trip.commentCount = (trip.commentCount || 0) + 1;
        this.setData({
          trip
        });
        return this.loadComments(1);
      })
      .then(() => {})
      .catch(err => {
        wx.showToast({
          title: '评论失败',
          icon: 'none'
        });
      });
  },

  toggleCommentLike(e) {
    if (!auth.checkLogin()) return;
    const commentId = e.currentTarget.dataset.id;
    const updateComments = (list, targetId) => list.map(c => {
      if (c.id === targetId) {
        var liked = !c.isLiked;
        return {
          ...c,
          isLiked: liked,
          likeCount: liked ? c.likeCount + 1 : c.likeCount - 1
        };
      }
      if (c.replies && c.replies.length > 0) return {
        ...c,
        replies: updateComments(c.replies, targetId)
      };
      return c;
    });
    var targetComment = null;
    (function find(list, id) {
      for (var i = 0; i < list.length; i++) {
        if (list[i].id === id) {
          targetComment = list[i];
          return;
        }
        if (list[i].replies) find(list[i].replies, id);
      }
    })(this.data.comments, commentId);
    if (!targetComment) return;
    var isLiked = !targetComment.isLiked;
    var likeCount = isLiked ? targetComment.likeCount + 1 : targetComment.likeCount - 1;
    this.setData({
      comments: updateComments(this.data.comments, commentId)
    });
    api.toggleLike({
        targetId: commentId,
        targetType: 'comment'
      })
      .catch(() => {
        var rollback = (list, id) => list.map(c => {
          if (c.id === id) return {
            ...c,
            isLiked: !isLiked,
            likeCount: likeCount
          };
          if (c.replies) return {
            ...c,
            replies: rollback(c.replies, id)
          };
          return c;
        });
        this.setData({
          comments: rollback(this.data.comments, commentId)
        });
      });
  },

  deleteComment(e) {
    var id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这条评论吗？',
      confirmText: '删除',
      confirmColor: '#ff4d4f',
      success: function (res) {
        if (res.confirm) this.doDeleteComment(id);
      }.bind(this)
    });
  },

  doDeleteComment(commentId) {
    if (!auth.checkLogin()) return;
    api.deleteComment(commentId)
      .then(() => {
        var removeComment = function (list, id) {
          return list.filter(function (c) {
            return c.id !== id;
          }).map(function (c) {
            if (c.replies && c.replies.length > 0) return {
              ...c,
              replies: removeComment(c.replies, id)
            };
            return c;
          });
        };
        var newComments = removeComment(this.data.comments, commentId);
        var trip = {
          ...this.data.trip
        };
        trip.commentCount = Math.max(0, (trip.commentCount || 1) - 1);
        this.setData({
          comments: newComments,
          trip
        });
      })
      .catch(() => {
        wx.showToast({
          title: '删除失败',
          icon: 'none'
        });
      });
  },

  // ===== 地图相关（完全参照 pages/map/map.js 实现） =====

  async loadMapDay(dayIndex) {
    var trip = this.data.trip;
    if (!trip || !trip.dayList) return;

    var dayData = trip.dayList[dayIndex];
    if (!dayData || !dayData.spots || dayData.spots.length === 0) {
      this.setData({
        markers: [],
        polylines: [],
        routeLoaded: false
      });
      return;
    }

    this.setData({
      loadingRoute: true,
      routeLoaded: false,
      mapDayDate: dayData.date || ''
    });

    var city = trip.location || trip.destination || '';
    var pointsWithCoords = [];

    for (var i = 0; i < dayData.spots.length; i++) {
      var p = dayData.spots[i];
      var lat = 0;
      var lng = 0;

      if (p.latitude && p.longitude) {
        lat = p.latitude;
        lng = p.longitude;
      } else {
        // 一层：POI 搜索
        try {
          var pois = await tmap.searchPOI(p.name, city);
          if (pois && pois.length > 0) {
            lng = pois[0].longitude;
            lat = pois[0].latitude;
          }
        } catch (e) {
          console.warn('[loadMapDay] POI失败:', p.name, e);
        }

        // 二层：地名 geocode
        if (!lat || !lng) {
          try {
            var geo = await tmap.geocode(p.name, city);
            if (geo && geo.lat && geo.lng) {
              lat = geo.lat;
              lng = geo.lng;
            }
          } catch (e2) {
            console.warn('[loadMapDay] geocode失败:', p.name, e2);
          }
        }

        // 三层：地址 geocode
        if ((!lat || !lng) && p.address) {
          try {
            var geo2 = await tmap.geocode(p.address, '');
            if (geo2 && geo2.lat && geo2.lng) {
              lat = geo2.lat;
              lng = geo2.lng;
            }
          } catch (e3) {
            console.warn('[loadMapDay] 地址编码失败:', p.address, e3);
          }
        }

        // 四层：兜底坐标（调用后端城市表）
        if (!lat || !lng) {
          var fb = await mapUtils.getFallbackCoordsAsync(i, dayData.spots.length, city);
          lat = fb.lat;
          lng = fb.lng;
          console.warn('[loadMapDay] 兜底坐标:', p.name, lat, lng);
        }
      }

      pointsWithCoords.push({
        id: p.id || ('mp_' + i),
        name: p.name || ('途经点' + (i + 1)),
        arrivalTime: p.arrivalTime || '',
        duration: p.duration || 60,
        latitude: lat,
        longitude: lng,
        address: p.address || '',
        type: i === 0 ? 'start' : (i === dayData.spots.length - 1 ? 'end' : 'spot')
      });
    }

    var centerLat = (pointsWithCoords[0] && pointsWithCoords[0].latitude) || 24.4798;
    var centerLng = (pointsWithCoords[0] && pointsWithCoords[0].longitude) || 118.0894;

    var markers = mapUtils.buildMapMarkers(pointsWithCoords, {
      startColor: '#2EC4B6',
      endColor: '#2EC4B6',
      spotColor: '#2EC4B6'
    });
    markers = markers.map(function(marker, index) {
      return Object.assign({}, marker, {
        iconPath: '/images/tab-trips-active.png',
        width: 1,
        height: 1,
        anchor: { x: 0.5, y: 0.5 },
        label: Object.assign({}, marker.label, {
          content: String(index + 1),
          color: '#FFFFFF',
          bgColor: '#2EC4B6',
          fontSize: 13,
          padding: 6,
          borderRadius: 18,
          textAlign: 'center'
        })
      });
    });
    var polylines = pointsWithCoords.length >= 2 ? mapUtils.buildStraightPolyline(pointsWithCoords, '#2EC4B6') : [];

    var totalDistance = 0;
    for (var j = 1; j < pointsWithCoords.length; j++) {
      totalDistance += tmap.calculateDistance(
        pointsWithCoords[j - 1].longitude, pointsWithCoords[j - 1].latitude,
        pointsWithCoords[j].longitude, pointsWithCoords[j].latitude
      );
    }

    this.setData({
      activeDay: dayIndex,
      mapData: {
        longitude: centerLng,
        latitude: centerLat,
        scale: 14
      },
      markers: markers,
      polylines: polylines,
      routeStats: {
        distance: (totalDistance / 1000).toFixed(1),
        duration: mapUtils.estimateDuration(pointsWithCoords),
        points: pointsWithCoords,
        spots: pointsWithCoords.length
      },
      routeLoaded: false
    });

    setTimeout(() => {
      mapUtils.fitMapToPoints('trip-map', pointsWithCoords);
    }, 300);

    if (pointsWithCoords.length >= 2) {
      this.loadRealRoute(pointsWithCoords);
    } else {
      this.setData({
        loadingRoute: false
      });
    }
  },

  buildMapMarkers(points) {
    return mapUtils.buildMapMarkers(points);
  },

  loadRealRoute(points) {
    var origin = points[0].longitude + ',' + points[0].latitude;
    var dest = points[points.length - 1].longitude + ',' + points[points.length - 1].latitude;
    var waypoints = points.slice(1, -1).map(function (p) {
      return p.longitude + ',' + p.latitude;
    });
    var mode = this.data.travelMode;
    var city = this.data.trip ? (this.data.trip.location || this.data.trip.destination || '') : '';

    this.setData({
      loadingRoute: true
    });
    var self = this;

    tmap.getRoute(origin, dest, waypoints, mode, city)
      .then(function (route) {
        if (route.paths && route.paths.length > 0) {
          self._rawPaths = route.paths;
          var allPathsData = route.paths.map(function (path, idx) {
            return {
              index: idx,
              distance: path.distance,
              duration: path.duration,
              label: mapUtils.getPathLabel(idx, path, route.paths.length),
              distanceText: (path.distance / 1000).toFixed(1) + 'km'
            };
          });
          self.setData({
            allPaths: allPathsData,
            totalPaths: route.paths.length,
            currentPathIndex: 0
          });
          self.renderPath(route.paths[0], mode, points);
        } else {
          self.setData({
            loadingRoute: false
          });
        }
      })
      .catch(function (error) {
        console.error('[loadRealRoute] 失败:', error);
        self.setData({
          loadingRoute: false
        });
      });
  },

  renderPath(path, mode, points) {
    var coordPoints;
    if (path.polyline_decoded && path.polyline && path.polyline.length > 0) {
      coordPoints = mapUtils.convertDecodedPolyline(path.polyline);
    } else {
      coordPoints = mapUtils.decodePolyline(path.polyline);
    }
    if (coordPoints.length === 0) {
      this.setData({
        loadingRoute: false
      });
      return;
    }

    var polylines = [{
      points: coordPoints,
      color: '#2EC4B6',
      width: 6,
      arrowLine: mode !== 'walk',
      dottedLine: mode === 'walk',
      borderColor: '#FFFFFF',
      borderWidth: 2
    }];

    var oldMarkers = this.data.markers || [];
    var baseMarkers = [];
    for (var mi = 0; mi < oldMarkers.length; mi++) {
      if (oldMarkers[mi].id < 1000) baseMarkers.push(oldMarkers[mi]);
    }

    this.setData({
      markers: baseMarkers,
      polylines: polylines,
      routeLoaded: true,
      loadingRoute: false,
      'routeStats.distance': (path.distance / 1000).toFixed(1),
      'routeStats.duration': mapUtils.formatDuration(path.duration)
    });

    var self = this;
    setTimeout(function () {
      self.fitMapToPoints(self.data.routeStats.points);
    }, 100);
  },

  fitMapToPoints(points) {
    mapUtils.fitMapToPoints('trip-map', points);
  },

  openAmapNavigation(points) {
    var mode = this.data.travelMode;
    mapUtils.openAmapNavigation(points, mode);
  },

  openTencentNavigation(points) {
    var mode = this.data.travelMode;
    var app = getApp();
    mapUtils.openTencentNavigation(points, mode, app.globalData.tmapKey);
  },

  // ===== 地图 UI 交互 =====

  switchMapDay(e) {
    var day = parseInt(e.currentTarget.dataset.day);
    if (day !== this.data.activeDay) {
      var dayList = this.data.trip.dayList;
      if (dayList && dayList[day]) {
        this.setData({
          activeDay: day,
          currentDayItem: dayList[day]
        });
        this.loadMapDay(day);
      }
    }
  },

  toggleMapPathPicker() {
    this.setData({
      showPathPicker: !this.data.showPathPicker
    });
  },

  switchMapPath(e) {
    var index = parseInt(e.detail.index);
    if (index === this.data.currentPathIndex) {
      this.setData({
        showPathPicker: false
      });
      return;
    }
    var points = this.data.routeStats.points;
    var mode = this.data.travelMode;
    var self = this;
    if (this._rawPaths && this._rawPaths.length > index) {
      this.setData({
        currentPathIndex: index,
        showPathPicker: false
      });
      this.renderPath(this._rawPaths[index], mode, points);
      return;
    }
    this.setData({
      currentPathIndex: index,
      showPathPicker: false,
      loadingRoute: true
    });
    if (points && points.length >= 2) {
      var origin = points[0].longitude + ',' + points[0].latitude;
      var dest = points[points.length - 1].longitude + ',' + points[points.length - 1].latitude;
      var waypoints = points.slice(1, -1).map(function (p) {
        return p.longitude + ',' + p.latitude;
      });
      var city = this.data.trip ? (this.data.trip.location || this.data.trip.destination || '') : '';
      tmap.getRoute(origin, dest, waypoints, mode, city)
        .then(function (route) {
          if (route.paths && route.paths.length > index) {
            self._rawPaths = route.paths;
            self.renderPath(route.paths[index], mode, points);
          } else {
            self.setData({
              loadingRoute: false
            });
          }
        }).catch(function () {
          self.setData({
            loadingRoute: false
          });
        });
    }
  },

  onMapMarkerTap(e) {
    var point = this.data.routeStats.points[e.detail.markerId - 1];
    if (point) wx.showToast({
      title: point.name + (point.arrivalTime ? ' ' + point.arrivalTime : ''),
      icon: 'none',
      duration: 2000
    });
  },

  /** 途经点点击（来自 waypoint-timeline 组件） */
  onMapWaypointTap(e) {
    var point = this.data.routeStats.points[e.detail.index];
    if (point) wx.showToast({
      title: point.name + (point.arrivalTime ? ' ' + point.arrivalTime : ''),
      icon: 'none',
      duration: 2000
    });
  },

  goMapNavigation() {
    var points = this.data.routeStats.points;
    if (!points || points.length < 2) {
      wx.showToast({
        title: '至少需要 2 个途经点',
        icon: 'none'
      });
      return;
    }
    var self = this;
    wx.showActionSheet({
      itemList: ['高德地图（推荐）', '腾讯地图'],
      success: function (res) {
        if (res.tapIndex === 0) self.openAmapNavigation(points);
        else self.openTencentNavigation(points);
      }
    });
  },

  // ===== 景点展开/收起 =====
  toggleSpotExpand(e) {
    var spotId = e.currentTarget.dataset.spotId;
    var currentDayItem = this.data.currentDayItem;
    var spots = currentDayItem.spots || [];
    var spotIndex = spots.findIndex(function(s) { return s.id === spotId; });
    
    if (spotIndex >= 0) {
      var spot = spots[spotIndex];
      var current = spot._expanded || false;
      var activeDay = this.data.activeDay;
      
      // 同时更新 currentDayItem 和 trip.dayList，保持数据同步
      var updateData = {};
      updateData['currentDayItem.spots[' + spotIndex + ']._expanded'] = !current;
      updateData['trip.dayList[' + activeDay + '].spots[' + spotIndex + ']._expanded'] = !current;
      
      this.setData(updateData);
    }
  },

  // ===== 编辑游玩推荐 =====
  editTravelGuide(e) {
    var spotId = e.currentTarget.dataset.spotId;
    var spotName = e.currentTarget.dataset.spotName;

    // 查找当前景点的 travelGuide
    var currentDayItem = this.data.currentDayItem;
    var spots = currentDayItem.spots || [];
    var spot = spots.find(function(s) { return s.id === spotId; });
    var currentGuide = spot && spot.travelGuide ? spot.travelGuide : '';

    this.setData({
      showGuideModal: true,
      editingSpot: { id: spotId, name: spotName },
      editingGuide: currentGuide
    });
  },

  closeGuideModal() {
    this.setData({
      showGuideModal: false,
      editingSpot: null,
      editingGuide: ''
    });
  },

  onGuideInput(e) {
    this.setData({
      editingGuide: e.detail.value
    });
  },

  saveTravelGuide() {
    if (!auth.checkLogin()) return;
    var self = this;
    var spotId = this.data.editingSpot.id;
    var guide = this.data.editingGuide.trim();

    if (!guide) {
      wx.showToast({ title: '请输入游玩推荐', icon: 'none' });
      return;
    }

    tripApi.updateSpot(spotId, { travelGuide: guide })
      .then(function() {
        // 更新本地数据
        var trip = self.data.trip;
        var dayList = trip.dayList || [];
        var updated = false;

        for (var i = 0; i < dayList.length && !updated; i++) {
          var spots = dayList[i].spots || [];
          for (var j = 0; j < spots.length; j++) {
            if (spots[j].id === spotId) {
              dayList[i].spots[j].travelGuide = guide;
              updated = true;
              break;
            }
          }
        }

        // 更新 allSpots
        var allSpots = self.data.allSpots || [];
        for (var k = 0; k < allSpots.length; k++) {
          if (allSpots[k].id === spotId) {
            allSpots[k].travelGuide = guide;
            break;
          }
        }

        // 更新 currentDayItem
        var currentDayItem = self.data.currentDayItem;
        var currentSpots = currentDayItem.spots || [];
        for (var m = 0; m < currentSpots.length; m++) {
          if (currentSpots[m].id === spotId) {
            currentSpots[m].travelGuide = guide;
            break;
          }
        }

        self.setData({
          trip: trip,
          allSpots: allSpots,
          currentDayItem: currentDayItem,
          showGuideModal: false,
          editingSpot: null,
          editingGuide: ''
        });
      })
      .catch(function(err) {
        wx.showToast({ title: '保存失败', icon: 'none' });
        console.error('[saveTravelGuide] 失败:', err);
      });
  }
});
