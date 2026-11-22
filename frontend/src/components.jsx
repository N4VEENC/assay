import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from './auth.jsx';

export const toast = (m) => window.dispatchEvent(new CustomEvent('toast', { detail: m }));
export function Toast() {
  const [m, setM] = useState('');
  useEffect(() => {
    let t;
    const f = (e) => { setM(e.detail); clearTimeout(t); t = setTimeout(() => setM(''), 2200); };
    window.addEventListener('toast', f);
    return () => window.removeEventListener('toast', f);
  }, []);
  return <div className={'toast' + (m ? ' is-visible' : '')}>{m}</div>;
}

export const Switch = ({ on, onClick }) => <button className={'switch' + (on ? ' is-on' : '')} onClick={onClick}><i /></button>;

export function DD({ value, items, onChange, label }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const close = (e) => { if (!e.target.closest('.dd')) setOpen(false); };
    document.addEventListener('click', close);
    const key = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('keydown', key);
    return () => { document.removeEventListener('click', close); document.removeEventListener('keydown', key); };
  }, []);
  const item = items.find((x) => x.value === value);
  return <div className="dd"><button type="button" className="dd__btn" aria-expanded={open} onClick={() => setOpen(!open)}><span>{label && <i className="dd__icon">{item?.icon || '·'}</i>}{item?.label || value || 'Choose'}</span><span className="dd__caret">⌄</span></button>{open && <div className="dd__menu" role="listbox">{items.map((x) => <button type="button" role="option" aria-selected={x.value === value} key={x.value} className={'dd__item' + (x.value === value ? ' is-selected' : '')} onClick={() => { onChange(x.value); setOpen(false); }}><i className="dd__icon">{x.icon || '·'}</i>{x.label}</button>)}</div>}</div>;
}

export const fmt = (d) => (d ? new Date(d).toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—');

export function AdminBar({ id, active }) {
  const { user, logout } = useAuth();
  return (
    <div className="topbar">
      <div className="brand">assay<i>.</i></div>
      <Link className={'topbar__tab' + (active === 'forms' ? ' is-active' : '')} to="/admin/forms">Forms</Link>
      {id && <Link className={'topbar__tab' + (active === 'builder' ? ' is-active' : '')} to={`/admin/forms/${id}/builder`}>Builder</Link>}
      {id && <Link className={'topbar__tab' + (active === 'responses' ? ' is-active' : '')} to={`/admin/forms/${id}/responses`}>Responses</Link>}
      <span className="spacer" />
      <span className="eyebrow">{user?.name} · Admin</span>
      <button className="link-btn" onClick={logout}>Sign out</button>
    </div>
  );
}
