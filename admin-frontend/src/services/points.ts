import request from '../utils/request';

export const pointsService = {
  records: (params: any) => request.get('/points/records', { params }),
  config: () => request.get('/points/config'),
  updateConfig: (id: number, data: any) => request.put(`/points/config/${id}`, data),
  manualPoints: (params: any) => request.post('/points/manual', null, { params }),
  signLog: (params: any) => request.get('/points/sign-log', { params }),
};
