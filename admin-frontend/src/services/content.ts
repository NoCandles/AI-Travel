import request from '../utils/request';

export const contentService = {
  listPublish: (params: any) => request.get('/content/publish', { params }),
  updatePublishStatus: (id: string, reviewStatus: number, reason?: string) =>
    request.put(`/content/publish/${id}/status`, null, { params: { reviewStatus, reason } }),
  deletePublish: (id: string) => request.delete(`/content/publish/${id}`),
  listComments: (params: any) => request.get('/content/comments', { params }),
  deleteComment: (id: number) => request.delete(`/content/comments/${id}`),
};
