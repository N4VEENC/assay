import { useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Guard, home, useAuth } from './auth.jsx';
import Login from './pages/Login.jsx';
import Forms from './pages/Forms.jsx';
import Builder from './pages/Builder.jsx';
import Landing from './pages/Landing.jsx';
import Fill from './pages/Fill.jsx';
import { Responses, Person } from './pages/Responses.jsx';

function Home() {
  const { user, loading } = useAuth();
  if (loading) return <div className="page muted">Loading</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role === 'ADMIN') return <Navigate to={home(user)} replace />;
  return <div className="page"><div className="brand">assay<i>.</i></div><p className="muted home-note">Open the link you received to start an assessment.</p></div>;
}

export default function App() {
  useEffect(() => {
    const track = document.createElement('div');
    const thumb = document.createElement('div');
    track.className = 'scrollbar-overlay';
    track.setAttribute('role', 'scrollbar');
    track.setAttribute('aria-label', 'Page scrollbar');
    track.setAttribute('aria-orientation', 'vertical');
    track.tabIndex = 0;
    thumb.className = 'scrollbar-overlay__thumb';
    track.appendChild(thumb);
    document.body.appendChild(track);

    let activeTarget = null;
    let isDocument = false;
    let hideTimer;
    let frame = 0;
    const getScrollTop = () => isDocument ? (document.scrollingElement?.scrollTop || 0) : activeTarget?.scrollTop || 0;
    const setScrollTop = (value) => isDocument ? window.scrollTo(0, value) : activeTarget && (activeTarget.scrollTop = value);
    const draw = () => {
      frame = 0;
      if (!activeTarget) return;
      const height = isDocument ? window.innerHeight : activeTarget.clientHeight;
      const scrollHeight = isDocument ? document.documentElement.scrollHeight : activeTarget.scrollHeight;
      const maxScroll = scrollHeight - height;
      if (height <= 0 || maxScroll <= 2) { track.classList.remove('is-visible'); return; }
      const rect = isDocument ? { top: 0, right: window.innerWidth } : activeTarget.getBoundingClientRect();
      const thumbHeight = Math.max(28, height * height / scrollHeight);
      const travel = Math.max(0, height - thumbHeight);
      const top = rect.top + (getScrollTop() / maxScroll) * travel;
      track.style.top = `${Math.max(0, rect.top)}px`;
      track.style.right = `${Math.max(0, window.innerWidth - rect.right + 2)}px`;
      track.style.height = `${Math.min(height, window.innerHeight - Math.max(0, rect.top))}px`;
      thumb.style.height = `${thumbHeight}px`;
      thumb.style.transform = `translateY(${Math.max(0, top - rect.top)}px)`;
      track.setAttribute('aria-valuemin', '0');
      track.setAttribute('aria-valuemax', String(Math.round(maxScroll)));
      track.setAttribute('aria-valuenow', String(Math.round(getScrollTop())));
      track.classList.add('is-visible');
      clearTimeout(hideTimer);
      hideTimer = setTimeout(() => track.classList.remove('is-visible'), 700);
    };
    const showForScroll = (event) => {
      const target = event.target;
      isDocument = target === document || target === document.documentElement || target === document.body;
      activeTarget = isDocument ? (document.scrollingElement || document.documentElement) : target instanceof HTMLElement ? target : null;
      if (!activeTarget || (activeTarget.scrollHeight <= activeTarget.clientHeight + 2)) return;
      if (!frame) frame = requestAnimationFrame(draw);
    };
    const onPointerDown = (event) => {
      if (!activeTarget) return;
      event.preventDefault();
      thumb.setPointerCapture(event.pointerId);
      track.classList.add('is-dragging');
      thumb.dataset.startY = String(event.clientY);
      thumb.dataset.startScroll = String(getScrollTop());
    };
    const onPointerMove = (event) => {
      if (!track.classList.contains('is-dragging') || !activeTarget) return;
      const height = isDocument ? window.innerHeight : activeTarget.clientHeight;
      const thumbHeight = Math.max(28, height * height / (isDocument ? document.documentElement.scrollHeight : activeTarget.scrollHeight));
      const travel = Math.max(1, height - thumbHeight);
      const maxScroll = (isDocument ? document.documentElement.scrollHeight : activeTarget.scrollHeight) - height;
      const delta = event.clientY - Number(thumb.dataset.startY || 0);
      setScrollTop(Math.max(0, Math.min(maxScroll, Number(thumb.dataset.startScroll || 0) + delta * maxScroll / travel)));
      if (!frame) frame = requestAnimationFrame(draw);
    };
    const stopDrag = () => track.classList.remove('is-dragging');
    const onKeyDown = (event) => {
      if (!activeTarget) return;
      const height = isDocument ? window.innerHeight : activeTarget.clientHeight;
      const current = getScrollTop();
      const max = (isDocument ? document.documentElement.scrollHeight : activeTarget.scrollHeight) - height;
      const step = event.key === 'PageDown' || event.key === 'PageUp' ? height * .8 : 48;
      if (event.key === 'ArrowDown' || event.key === 'PageDown') setScrollTop(Math.min(max, current + step));
      else if (event.key === 'ArrowUp' || event.key === 'PageUp') setScrollTop(Math.max(0, current - step));
      else if (event.key === 'Home') setScrollTop(0);
      else if (event.key === 'End') setScrollTop(max);
      else return;
      event.preventDefault();
      if (!frame) frame = requestAnimationFrame(draw);
    };
    document.addEventListener('scroll', showForScroll, { capture: true, passive: true });
    thumb.addEventListener('pointerdown', onPointerDown);
    thumb.addEventListener('pointermove', onPointerMove);
    thumb.addEventListener('pointerup', stopDrag);
    thumb.addEventListener('pointercancel', stopDrag);
    track.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('scroll', showForScroll, true);
      thumb.removeEventListener('pointerdown', onPointerDown);
      thumb.removeEventListener('pointermove', onPointerMove);
      thumb.removeEventListener('pointerup', stopDrag);
      thumb.removeEventListener('pointercancel', stopDrag);
      track.removeEventListener('keydown', onKeyDown);
      clearTimeout(hideTimer);
      if (frame) cancelAnimationFrame(frame);
      track.remove();
    };
  }, []);
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login mode="login" />} />
      <Route path="/register" element={<Login mode="register" />} />
      <Route path="/f/:token" element={<Landing />} />
      <Route path="/admin/forms" element={<Guard role="ADMIN"><Forms /></Guard>} />
      <Route path="/admin/forms/:id/builder" element={<Guard role="ADMIN"><Builder /></Guard>} />
      <Route path="/admin/forms/:id/responses" element={<Guard role="ADMIN"><Responses /></Guard>} />
      <Route path="/admin/forms/:id/responses/:sid" element={<Guard role="ADMIN"><Person /></Guard>} />
      <Route path="/fill/:id" element={<Guard role="RESPONDENT"><Fill /></Guard>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
