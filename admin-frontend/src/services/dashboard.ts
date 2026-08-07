import request from '../utils/request';

export const dashboardService = {
  overview: () => request.get('/dashboard/overview'),
  trends: () => request.get('/dashboard/trends'),
};
