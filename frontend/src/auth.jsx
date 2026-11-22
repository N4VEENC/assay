import { createContext, useContext, useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { api, refresh, setToken } from './api';

const Ctx = createContext(null);
export const useAuth = () => useContext(Ctx);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => { refresh().then((d) => setUser(d.user)).catch(() => {}).finally(() => setLoading(false)); }, []);
  const accept = (d) => { setToken(d.accessToken); setUser(d.user); return d.user; };
  const login = async (email, password) => accept((await api.post('/auth/login', { email, password })).data);
  const register = async (name, email, password) => accept((await api.post('/auth/register', { name, email, password })).data);
  const logout = async () => { try { await api.post('/auth/logout'); } catch { /* ignore */ } setToken(null); setUser(null); window.location.href = '/login'; };
  return <Ctx.Provider value={{ user, loading, login, register, logout }}>{children}</Ctx.Provider>;
}

export const home = (u) => (u?.role === 'ADMIN' ? '/admin/forms' : '/');

export function Guard({ role, children }) {
  const { user, loading } = useAuth();
  const loc = useLocation();
  if (loading) return <div className="page muted">Loading</div>;
  if (!user) return <Navigate to={'/login?returnTo=' + encodeURIComponent(loc.pathname)} replace />;
  if (role && user.role !== role) return <Navigate to={home(user)} replace />;
  return children;
}
