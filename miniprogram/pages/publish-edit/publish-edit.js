const api = require('../../utils/api');
const auth = require('../../utils/auth');
const { applyTheme } = require('../../utils/theme');
const share = require('../../utils/share');

const app = getApp();

Page({
  data: {
    darkMode: wx.getStorageSync('darkMode') || false,
    loading: true,
    submitting: false,
    tripPlanId: '',
    publishId: '',
    title: '',
    description: '',
    content: '',
    coverImage: '',
    location: '',
    days: 1,
    nights: 1,
    tags: [],
    tagInput: '',
    hotTags: ['亲子', '美食', '小众', '周末', '自驾', '徒步', '避暑', '摄影'],
    candidateImages: [],
    selectedImages: [],
    maxImages: 9
  },

  onLoad(options) {
    if (!auth.checkLogin()) return;
    applyTheme(this);
    share.enableShareMenu();

    if (options.publishId) {
      wx.redirectTo({
        url: `/pages/planner/planner?publishId=${options.publishId}&tab=publish`
      });
      return;
    }

    if (options.tripPlanId) {
      wx.redirectTo({
        url: `/pages/planner/planner?id=${options.tripPlanId}&tab=publish`
      });
      return;
    }

    this.setData({
      tripPlanId: options.tripPlanId || '',
      publishId: options.publishId || ''
    });

    if (options.publishId) {
      this.loadPublishDetail(options.publishId);
    } else if (options.tripPlanId) {
      this.loadPublishDraft(options.tripPlanId);
    } else {
      this.setData({ loading: false });
      wx.showToast({ title: '缺少行程信息', icon: 'none' });
    }
  },

  loadPublishDraft(tripPlanId) {
    this.setData({ loading: true });
    api.getPublishDraft(tripPlanId)
      .then((data) => this.hydratePublish(data || {}))
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '草稿加载失败', icon: 'none' });
      });
  },

  loadPublishDetail(publishId) {
    this.setData({ loading: true });
    const userId = wx.getStorageSync('userId') || '';
    api.getPublishDetail(publishId, userId)
      .then((data) => this.hydratePublish(data || {}))
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '发布加载失败', icon: 'none' });
      });
  },

  hydratePublish(data) {
    const candidateImages = this.buildCandidateImages(data);
    const selectedImages = (data.images && data.images.length ? data.images : candidateImages.slice(0, this.data.maxImages))
      .map((item, index) => this.normalizeImage(item, index))
      .filter(item => item.imageUrl);
    const coverImage = data.coverImage || this.getFirstImageUrl(selectedImages) || '';

    this.setData({
      loading: false,
      publishId: data.id || this.data.publishId,
      tripPlanId: data.tripPlanId || this.data.tripPlanId,
      title: data.title || '',
      description: data.description || '',
      content: data.content || '',
      coverImage,
      location: data.location || data.destination || '',
      days: data.days || (data.dayList ? data.dayList.length : 1),
      nights: data.nights || Math.max(1, (data.dayList ? data.dayList.length : 1) - 1),
      tags: Array.isArray(data.tags) ? data.tags : [],
      candidateImages: this.markCandidateImages(candidateImages, selectedImages),
      selectedImages: this.ensureCoverFirst(selectedImages, coverImage)
    });
  },

  buildCandidateImages(data) {
    const result = [];
    const seen = {};

    const pushImage = (image) => {
      const item = this.normalizeImage(image, result.length);
      if (!item.imageUrl || seen[item.imageUrl]) return;
      seen[item.imageUrl] = true;
      result.push(item);
    };

    pushImage({
      imageUrl: data.coverImage,
      imageType: 'cover',
      sourceType: 'trip_cover',
      sortOrder: 0
    });

    (data.images || []).forEach(pushImage);
    (data.dayList || []).forEach(day => {
      (day.spots || []).forEach(spot => {
        pushImage({
          imageUrl: spot.image || spot.coverImage,
          imageType: 'gallery',
          sourceType: 'spot_image',
          sourceId: spot.id
        });
      });
    });

    return result;
  },

  normalizeImage(image, index) {
    const imageUrl = image && (image.imageUrl || image.url || image.fileID || image.tempFilePath || image.path);
    const mediaType = image && (image.mediaType || image.type || image.fileType) || 'image';
    return {
      id: image && image.id,
      imageUrl: imageUrl || '',
      imageType: image && image.imageType || 'gallery',
      mediaType: mediaType === 'video' ? 'video' : 'image',
      sourceType: image && image.sourceType || 'upload',
      sourceId: image && image.sourceId || '',
      sortOrder: image && image.sortOrder != null ? image.sortOrder : index
    };
  },

  ensureCoverFirst(images, coverImage) {
    const list = images.slice();
    const coverIndex = list.findIndex(item => item.imageUrl === coverImage);
    if (coverIndex > 0) {
      const cover = list.splice(coverIndex, 1)[0];
      list.unshift(cover);
    }
    return list.map((item, index) => ({
      ...item,
      imageType: item.imageUrl === coverImage ? 'cover' : 'gallery',
      sortOrder: index
    }));
  },

  getFirstImageUrl(images) {
    const image = (images || []).find(item => (item.mediaType || 'image') !== 'video' && item.imageUrl);
    return image ? image.imageUrl : '';
  },

  markCandidateImages(candidateImages, selectedImages) {
    const selectedMap = {};
    selectedImages.forEach(item => {
      if (item.imageUrl) selectedMap[item.imageUrl] = true;
    });
    return candidateImages.map(item => ({
      ...item,
      selected: !!selectedMap[item.imageUrl]
    }));
  },

  onTitleInput(e) {
    this.setData({ title: e.detail.value });
  },

  onDescInput(e) {
    this.setData({ description: e.detail.value });
  },

  onContentInput(e) {
    this.setData({ content: e.detail.value });
  },

  onTagInput(e) {
    this.setData({ tagInput: e.detail.value });
  },

  addTag() {
    const tag = (this.data.tagInput || '').trim();
    if (!tag) return;
    this.addTagValue(tag);
    this.setData({ tagInput: '' });
  },

  toggleHotTag(e) {
    this.addTagValue(e.currentTarget.dataset.tag);
  },

  addTagValue(tag) {
    const tags = this.data.tags.slice();
    if (tags.includes(tag)) return;
    if (tags.length >= 6) {
      wx.showToast({ title: '最多添加6个标签', icon: 'none' });
      return;
    }
    tags.push(tag);
    this.setData({ tags });
  },

  removeTag(e) {
    const tag = e.currentTarget.dataset.tag;
    this.setData({
      tags: this.data.tags.filter(item => item !== tag)
    });
  },

  toggleImage(e) {
    const imageUrl = e.currentTarget.dataset.url;
    const image = this.data.candidateImages.find(item => item.imageUrl === imageUrl);
    if (!image) return;

    const selectedImages = this.data.selectedImages.slice();
    const index = selectedImages.findIndex(item => item.imageUrl === imageUrl);
    if (index >= 0) {
      selectedImages.splice(index, 1);
    } else {
      if (selectedImages.length >= this.data.maxImages) {
        wx.showToast({ title: '最多选择9张图片', icon: 'none' });
        return;
      }
      selectedImages.push(image);
    }

    const coverImage = selectedImages.find(item => item.imageUrl === this.data.coverImage && item.mediaType !== 'video')
      ? this.data.coverImage
      : this.getFirstImageUrl(selectedImages);
    this.setData({
      coverImage,
      selectedImages: this.ensureCoverFirst(selectedImages, coverImage),
      candidateImages: this.markCandidateImages(this.data.candidateImages, selectedImages)
    });
  },

  chooseCover(e) {
    const imageUrl = e.currentTarget.dataset.url;
    this.setData({
      coverImage: imageUrl,
      selectedImages: this.ensureCoverFirst(this.data.selectedImages, imageUrl)
    });
  },

  removeSelectedImage(e) {
    const imageUrl = e.currentTarget.dataset.url;
    const selectedImages = this.data.selectedImages.filter(item => item.imageUrl !== imageUrl);
    const coverImage = imageUrl === this.data.coverImage
      ? this.getFirstImageUrl(selectedImages)
      : this.data.coverImage;
    this.setData({
      coverImage,
      selectedImages: this.ensureCoverFirst(selectedImages, coverImage),
      candidateImages: this.markCandidateImages(this.data.candidateImages, selectedImages)
    });
  },

  uploadImages() {
    wx.chooseMedia({
      count: Math.max(1, this.data.maxImages - this.data.selectedImages.length),
      mediaType: ['image', 'video'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const files = res.tempFiles || [];
        if (!files.length) return;
        this.uploadChosenFiles(files);
      }
    });
  },

  uploadChosenFiles(files) {
    const userId = wx.getStorageSync('userId') || 'user';
    const tasks = files.map((file, index) => {
      const tempFilePath = file.tempFilePath;
      const mediaType = file.fileType || file.type || (tempFilePath.toLowerCase().includes('.mp4') ? 'video' : 'image');
      const suffix = (tempFilePath.split('.').pop() || (mediaType === 'video' ? 'mp4' : 'jpg')).toLowerCase();
      const cloudPath = `publish/${userId}/${Date.now()}_${index}.${suffix}`;
      return wx.cloud.uploadFile({ cloudPath, filePath: tempFilePath }).then(uploadRes => ({
        ...uploadRes,
        mediaType: mediaType === 'video' ? 'video' : 'image'
      }));
    });

    Promise.all(tasks)
      .then((results) => {
        const newImages = results.map((item, index) => this.normalizeImage({
          imageUrl: item.fileID,
          imageType: 'gallery',
          mediaType: item.mediaType,
          sourceType: 'upload',
          sortOrder: this.data.selectedImages.length + index
        }, index));
        const selectedImages = this.data.selectedImages.concat(newImages).slice(0, this.data.maxImages);
        const candidateImages = this.data.candidateImages.concat(newImages);
    const coverImage = this.data.coverImage || this.getFirstImageUrl(selectedImages);
        this.setData({
          coverImage,
          selectedImages: this.ensureCoverFirst(selectedImages, coverImage),
          candidateImages: this.markCandidateImages(candidateImages, selectedImages)
        });
      })
      .catch(() => {
        wx.showToast({ title: '上传失败', icon: 'none' });
      });
  },

  previewImage(e) {
    const current = e.currentTarget.dataset.url;
    const urls = this.data.selectedImages.map(item => item.imageUrl);
    wx.previewImage({ current, urls });
  },

  submitPublish() {
    if (this.data.submitting) return;
    const title = (this.data.title || '').trim();
    if (!title) {
      wx.showToast({ title: '请填写标题', icon: 'none' });
      return;
    }
    if (!this.data.coverImage) {
      wx.showToast({ title: '请选择封面图', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    api.createPublish({
      tripPlanId: this.data.tripPlanId,
      title,
      description: (this.data.description || '').trim(),
      content: (this.data.content || '').trim(),
      coverImage: this.data.coverImage,
      location: this.data.location || '',
      days: this.data.days || 1,
      nights: this.data.nights || 1,
      tags: JSON.stringify(this.data.tags || []),
      images: this.ensureCoverFirst(this.data.selectedImages, this.data.coverImage).map((item, index) => ({
        imageUrl: item.imageUrl,
        imageType: item.imageUrl === this.data.coverImage ? 'cover' : 'gallery',
        mediaType: item.mediaType || 'image',
        sourceType: item.sourceType || 'upload',
        sourceId: item.sourceId || '',
        sortOrder: index
      }))
    }).then((publishId) => {
      return this.syncTripCoverAfterPublish().then(() => publishId).catch(() => publishId);
    }).then((publishId) => {
      this.setData({ submitting: false });
      app.globalData.statsDirty = true;
      app.globalData.squareDirty = true;
      app.globalData.tripListDirty = true;
      setTimeout(() => {
        wx.redirectTo({
          url: `/pages/trip-detail/trip-detail?publishId=${publishId}`
        });
      }, 700);
    }).catch(() => {
      this.setData({ submitting: false });
      wx.showToast({ title: '保存失败', icon: 'none' });
    });
  },

  syncTripCoverAfterPublish() {
    if (!this.data.tripPlanId || !this.data.coverImage) {
      return Promise.resolve();
    }
    return api.updateTrip(this.data.tripPlanId, {
      coverImage: this.data.coverImage,
      name: this.data.title || undefined,
      description: this.data.description || undefined
    });
  }
});
