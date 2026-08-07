import axios from 'axios';
import { message } from 'antd';

const request = axios.create({
  baseURL: '/api/admin',
  timeout: 15000,
});

// 请求拦截器 — 自动注入 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) {
    config.headers['X-Admin-Token'] = token;
  }
  return config;
});

// 响应拦截器 — 统一错误处理
request.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (data.success === false) {
      message.error(data.message || '操作失败');
      return Promise.reject(new Error(data.message));
    }
    return data;
  },
  (error) => {
    if (error.response) {
      const status = error.response.status;
      if (status === 401) {
        localStorage.removeItem('admin_token');
        localStorage.removeItem('admin_user');
        window.location.href = '/admin/login';
      } else {
        message.error(error.response.data?.message || '请求失败');
      }
    } else {
      message.error('网络错误，请检查后端服务');
    }
    return Promise.reject(error);
  }
);

export { request };
export default request;
