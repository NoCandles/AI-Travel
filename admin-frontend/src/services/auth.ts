import request from '../utils/request';

export const authService = {
  login: (username: string, password: string) =>
    request.post('/auth/login', { username, password }),
};
