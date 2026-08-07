import request from '../utils/request';

export const userService = {
  list: (params: any) => request.get('/users', { params }),
  detail: (id: string) => request.get(`/users/${id}`),
  updateStatus: (id: string, status: number) =>
    request.put(`/users/${id}/status`, null, { params: { status } }),
};
