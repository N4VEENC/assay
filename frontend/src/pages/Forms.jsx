import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, msg } from '../api';
import { AdminBar, fmt, toast } from '../components.jsx';

const DOT = { OPEN: 'ok', PUBLISHED: 'info', DRAFT: '', SAVED: 'warn', EXPIRED: 'bad', CLOSED: 'bad' };

export default function Forms() {
  const nav = useNavigate();
  const [rows, setRows] = useState(null);
  const [drawer, setDrawer] = useState(false);
  const [title, setTitle] = useState('');
  const [file, setFile] = useState(null);
  const [errs, setErrs] = useState([]);
  const load = () => api.get('/forms/overview').then((r) => setRows(r.data)).catch((e) => toast(msg(e)));
  useEffect(() => { load(); }, []);

  const create = async () => {
    setErrs([]);
    if (!title.trim()) { setErrs([{ message: 'Title is required' }]); return; }
    try {
      let id;
      if (file) {
        const fd = new FormData(); fd.append('title', title); fd.append('file', file);
        id = (await api.post('/forms/import', fd)).data.id;
      } else id = (await api.post('/forms', { title })).data.id;
      nav(`/admin/forms/${id}/builder`);
    } catch (e) { setErrs(e.response?.data?.importErrors || [{ message: msg(e) }]); }
  };

  return (
    <>
      <AdminBar active="forms" />
      <div className="page">
        <div className="toolbar toolbar--end"><button className="btn-solid btn-solid--xl" onClick={() => setDrawer(true)}>+ New form</button></div>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Form</th><th>Status</th><th>Assigned</th><th>Responded</th><th>Not responded</th><th>Submitted</th><th>Not submitted</th><th>Completion</th><th>Created</th><th>Expires</th><th></th></tr></thead>
            <tbody>
              {rows === null && <tr><td className="muted" colSpan="11">Loading</td></tr>}
              {rows?.length === 0 && <tr><td className="muted" colSpan="11">No forms yet.</td></tr>}
              {rows?.map((f) => {
                const a = f.assigned, r = f.responded, s = f.submitted, has = a != null;
                const den = has ? a : r;
                const pct = den ? Math.round((s / den) * 100) : 0;
                return (
                  <tr key={f.id}>
                    <td><b>{f.title}</b><br /><span className="muted mono">v{f.versionNo}</span></td>
                    <td><i className={'status-dot' + (DOT[f.displayStatus] ? ' status-dot--' + DOT[f.displayStatus] : '')} />{f.displayStatus}</td>
                    <td className="num">{has ? a : 'open'}</td>
                    <td className="num">{has ? `${r} / ${a}` : r}{den > 0 && <div className="line-bar"><span className="line-bar__fill" style={{ width: (r / den) * 100 + '%' }} /></div>}</td>
                    <td className="num">{has ? <><i className={'status-dot status-dot--' + (a - r ? 'bad' : 'ok')} />{a - r}</> : '—'}</td>
                    <td className="num">{den > 0 ? <><div className="meter">{Array.from({ length: 10 }, (_, i) => <i key={i} className={'meter__tick' + (i < Math.round((s / den) * 10) ? ' is-on' : '')} />)}</div>{s}{has ? ` / ${a}` : ''}</> : '—'}</td>
                    <td className="num">{has ? a - s : '—'}</td>
                    <td className="num">{den > 0 ? pct + '%' : '—'}{den > 0 && <div className="stack-bar"><span className="stack-bar__seg stack-bar__seg--ok" style={{ width: (s / den) * 100 + '%' }} /><span className="stack-bar__seg stack-bar__seg--warn" style={{ width: ((r - s) / den) * 100 + '%' }} /></div>}</td>
                    <td className="num">{fmt(f.createdAt)}</td>
                    <td className="num">{fmt(f.expiresAt)}</td>
                    <td><button className="link-btn" onClick={() => nav(`/admin/forms/${f.id}/${r > 0 ? 'responses' : 'builder'}`)}>Open →</button></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
      {drawer && (
        <div className="overlay"><div className="drawer">
          <span className="eyebrow">New form</span>
          <h2 className="h2">Create a form</h2>
          <label className="field-label">Title</label><input value={title} onChange={(e) => setTitle(e.target.value)} />
          <label className="field-label">CSV file (optional)</label><input type="file" accept=".csv" onChange={(e) => setFile(e.target.files[0] || null)} />
          <div className="import-errors">
            {errs.map((x, i) => <div key={i} className="error-text">{x.row ? `Row ${x.row}${x.column ? ' · ' + x.column : ''}: ` : ''}{x.message}</div>)}
          </div>
          <div className="row drawer-form-actions">
            <button className="btn-solid" onClick={create}>{file ? 'Import' : 'Start blank'}</button>
            <button className="link-btn link-btn--quiet" onClick={() => { setDrawer(false); setErrs([]); }}>Close</button>
          </div>
        </div></div>
      )}
    </>
  );
}
