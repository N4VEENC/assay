import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, msg } from '../api';
import { useAuth } from '../auth.jsx';
import { fmt } from '../components.jsx';

export default function Landing() {
  const { token } = useParams();
  const nav = useNavigate();
  const { user, loading, logout } = useAuth();
  const [i, setI] = useState(null);
  const [e, setE] = useState('');
  useEffect(() => { api.get('/public/forms/' + token).then((r) => setI(r.data)).catch(() => setE('This link is not valid.')); }, [token]);

  const start = async () => {
    if (!user) { nav('/login?returnTo=' + encodeURIComponent('/f/' + token)); return; }
    try { nav('/fill/' + (await api.post(`/fill/${token}/start`)).data.submissionId); } catch (x) { setE(msg(x)); }
  };
  if (loading || (!i && !e)) return <div className="page muted">Loading</div>;
  const note = i?.displayStatus === 'EXPIRED' ? `This form closed on ${fmt(i.expiresAt)}.` : i?.displayStatus === 'CLOSED' ? 'This form is closed.' : i?.displayStatus === 'NOT_OPEN' ? `This form opens on ${fmt(i.opensAt)}.` : '';
  return (
    <div className="page landing-page">
      <div className="brand">assay<i>.</i></div>
      {i && <>
        <span className="eyebrow landing-kicker">Assessment</span>
        <h1 className="h1 landing-title">{i.title}</h1>
        {i.description && <p className="muted">{i.description}</p>}
        {i.expiresAt && !note && <p className="mono muted landing-close">Closes {fmt(i.expiresAt)}</p>}
        <div className="landing-actions">
          {note ? <p className="error-text">{note}</p>
            : user?.role === 'ADMIN' ? <p className="error-text">You are signed in as admin. <button className="link-btn" onClick={logout}>Sign out</button> to answer.</p>
            : <button className="btn-solid btn-solid--xl" onClick={start}>{user ? 'Start / Continue →' : 'Log in to start →'}</button>}
        </div>
      </>}
      {e && <p className="error-text landing-error">{e}</p>}
    </div>
  );
}
