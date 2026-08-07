import request from '../utils/request';

export const systemService = {
  // 管理员
  listAdmins: () => request.get('/system/admins'),
  createAdmin: (data: any) => request.post('/system/admins', data),
  updateAdmin: (id: string, data: any) => request.put(`/system/admins/${id}`, data),

  // 日志
  listLogs: (params: any) => request.get('/system/logs', { params }),

  // 公告
  listAnnouncements: (params: any) => request.get('/system/announcements', { params }),
  createAnnouncement: (data: any) => request.post('/system/announcements', data),
  updateAnnouncement: (id: string, data: any) => request.put(`/system/announcements/${id}`, data),
  deleteAnnouncement: (id: string) => request.delete(`/system/announcements/${id}`),

  // 敏感词
  listSensitiveWords: (params: any) => request.get('/system/sensitive-words', { params }),
  createSensitiveWord: (data: any) => request.post('/system/sensitive-words', data),
  updateSensitiveWord: (id: number, data: any) => request.put(`/system/sensitive-words/${id}`, data),
  deleteSensitiveWord: (id: number) => request.delete(`/system/sensitive-words/${id}`),
};
