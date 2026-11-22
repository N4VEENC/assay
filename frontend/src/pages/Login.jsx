import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { msg } from '../api';
import { home, useAuth } from '../auth.jsx';

export default function Login({ mode }) {
  const reg = mode === 'register';
  const { login, register } = useAuth();
  const nav = useNavigate();
  const [q] = useSearchParams();
  const [f, setF] = useState({ name: '', email: '', password: '' });
  const [err, setErr] = useState('');
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const submit = async (e) => {
    e.preventDefault(); setErr('');
    try {
      const u = reg ? await register(f.name, f.email, f.password) : await login(f.email, f.password);
      nav(q.get('returnTo') || home(u), { replace: true });
    } catch (x) { setErr(msg(x)); }
  };
  const to = (p) => p + (q.get('returnTo') ? '?returnTo=' + encodeURIComponent(q.get('returnTo')) : '');
  return (
    <div className="auth-layout">
      <div>
        <div className="brand">assay<i>.</i></div>
        <h1>Build and review<br />assessments.</h1>
        <span className="eyebrow">Assay admin</span>
      </div>
      <div>
        <span className="eyebrow">{reg ? 'Create account' : 'Sign in'}</span>
        <form className="auth-box" onSubmit={submit}>
          <h2 className="h2">{reg ? 'Create your account' : 'Welcome back'}</h2>
          {reg && <><label className="field-label">Name</label><input value={f.name} onChange={set('name')} /></>}
          <label className="field-label">Email</label><input type="email" value={f.email} onChange={set('email')} />
          <label className="field-label">Password</label><input type="password" value={f.password} onChange={set('password')} />
          <div className="error-text login-error">{err}</div>
          <button className="btn-solid login-submit">{reg ? 'Create account →' : 'Sign in →'}</button>
          <p className="login-footer">
            {reg ? <Link className="link-btn" to={to('/login')}>I have an account</Link> : <Link className="link-btn" to={to('/register')}>Create a respondent account</Link>}
          </p>
        </form>
        <span />
      </div>
    </div>
  );
}
