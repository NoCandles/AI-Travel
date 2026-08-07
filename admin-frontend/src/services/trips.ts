import request from '../utils/request';

export const tripService = {
  list: (params: any) => request.get('/trips', { params }),
  detail: (id: string) => request.get(`/trips/${id}`),
  delete: (id: string) => request.delete(`/trips/${id}`),
};
