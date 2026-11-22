import axios from 'axios';
export const api = axios.create({ baseURL: '/api', withCredentials: true });
let token = null, refreshing = null;
export const setToken = (t) => { token = t; };
export const msg = (e) => e?.response?.data?.message || 'Something went wrong';

api.interceptors.request.use((c) => {
  const isAuth = c.url.startsWith('/auth/') && c.url !== '/auth/me';
  if (token && !isAuth) c.headers.Authorization = 'Bearer ' + token;
  return c;
});

export function refresh() {
  refreshing ??= axios.post('/api/auth/refresh', null, { withCredentials: true, headers: { 'X-Requested-With': 'assay' } })
    .then((r) => { token = r.data.accessToken; return r.data; })
    .finally(() => { refreshing = null; });
  return refreshing;
}

api.interceptors.response.use((r) => r, async (e) => {
  const c = e.config;
  if (e.response?.status === 401 && !c._retry && !c.url.startsWith('/auth/')) {
    c._retry = true;
    try { await refresh(); c.headers.Authorization = 'Bearer ' + token; return api(c); }
    catch { window.location.href = '/login'; }
  }
  return Promise.reject(e);
});
