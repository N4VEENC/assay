import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, msg } from '../api';
import { useAuth } from '../auth.jsx';
import { Switch, toast } from '../components.jsx';

const CHOICE = ['SINGLE', 'YES_NO', 'LIKERT', 'DROPDOWN', 'MULTI', 'RANKING'];
const has = (a) => !!a && ((a.optionIds?.length ?? 0) > 0 || (a.text ?? '').trim() !== '' || (a.number !== null && a.number !== undefined && a.number !== '') || !!a.date);

export default function Fill() {
  const { id } = useParams();
  const { user, logout } = useAuth();
  const [d, setD] = useState(null);
  const [ans, setAns] = useState({});
  const [cur, setCur] = useState(0);
  const [all, setAll] = useState(false);
  const [mode, setMode] = useState('ask');
  const [indexWidth, setIndexWidth] = useState(300);
  const [err, setErr] = useState('');
  const [saved, setSaved] = useState('Draft saved');
  const rv = useRef(0), dirty = useRef({}), timer = useRef(), busy = useRef(false), questionScroll = useRef(null);

  const load = async () => {
    try {
      const { data } = await api.get('/submissions/' + id);
      rv.current = data.rowVersion; setD(data);
      const m = {}; data.sections.forEach((s) => s.questions.forEach((q) => { if (q.answer) m[q.id] = q.answer; }));
      setAns(m);
      if (data.status === 'SUBMITTED') setMode((x) => (x === 'ask' ? 'done' : x));
    } catch (e) { toast(msg(e)); }
  };
  useEffect(() => { load(); }, [id]);

  const flush = async () => {
    if (busy.current) { timer.current = setTimeout(flush, 400); return; }
    const batch = Object.values(dirty.current);
    if (!batch.length) return;
    dirty.current = {}; busy.current = true;
    try {
      const r = await api.put(`/submissions/${id}/answers`, { answers: batch }, { headers: { 'If-Match': String(rv.current) } });
      rv.current = r.data.rowVersion; setSaved('Draft saved');
    } catch (e) {
      if (e.response?.status === 409) { toast('Updated elsewhere. Reloaded.'); await load(); } else { toast(msg(e)); await load(); }
    }
    busy.current = false;
  };

  if (!d) return <div className="page muted">Loading</div>;
  const Q = d.sections.flatMap((s) => s.questions.map((q) => ({ ...q, sec: s })));
  const st = (q) => { const a = ans[q.id]; return a?.state === 'REVIEW' ? 'review' : has(a) ? 'done' : 'pend'; };
  const done = Q.filter((q) => has(ans[q.id])).length;
  const first = (s) => Q.findIndex((x) => x.sec.id === s.id);

  const set = (q, p) => {
    const a = { questionId: q.id, state: 'ANSWERED', optionIds: [], text: null, number: null, date: null, remarks: null, ...ans[q.id], ...p };
    setAns((x) => ({ ...x, [q.id]: a })); dirty.current[q.id] = a; setSaved('Saving');
    clearTimeout(timer.current); timer.current = setTimeout(flush, 800);
  };
  const flag = (q, s) => set(q, { state: ans[q.id]?.state === s ? 'ANSWERED' : s });
  const pick = (q, o) => {
    const a = ans[q.id]?.optionIds || [];
    if (q.type === 'MULTI' || q.type === 'RANKING') set(q, { optionIds: a.includes(o.id) ? a.filter((x) => x !== o.id) : [...a, o.id] });
    else set(q, { optionIds: [o.id] });
  };
  const beginIndexResize = (e) => {
    e.preventDefault();
    const start = e.clientX, width = indexWidth;
    document.body.classList.add('is-resizing');
    const move = (event) => setIndexWidth(Math.min(520, Math.max(240, width + event.clientX - start)));
    const end = () => { document.removeEventListener('pointermove', move); document.removeEventListener('pointerup', end); document.body.classList.remove('is-resizing'); };
    document.addEventListener('pointermove', move);
    document.addEventListener('pointerup', end, { once: true });
  };

  const widget = (q) => {
    const a = ans[q.id] || {};
    if (CHOICE.includes(q.type)) return (
      <div className="choice-list">{q.options.map((o) => {
        const i = (a.optionIds || []).indexOf(o.id), on = i >= 0;
        return <button key={o.id} className={'choice' + (on ? ' is-selected' : '')} onClick={() => pick(q, o)}><span className="choice__mark">{q.type === 'RANKING' && on ? i + 1 : ''}</span>{o.label}</button>;
      })}</div>
    );
    if (['SCALE', 'RATING', 'NPS'].includes(q.type)) {
      const lo = q.type === 'NPS' ? 0 : q.type === 'SCALE' ? Number(q.config?.min ?? 1) : 1, hi = q.type === 'NPS' ? 10 : q.type === 'SCALE' ? Number(q.config?.max ?? 5) : 5;
      return <div className="scale-row">{Array.from({ length: hi - lo + 1 }, (_, k) => k + lo).map((n) => <button key={n} className={'choice' + (a.number !== null && a.number !== undefined && Number(a.number) === n ? ' is-selected' : '')} onClick={() => set(q, { number: n })}>{n}</button>)}</div>;
    }
    if (q.type === 'LONG') return <textarea className="long-answer-input" rows={6} placeholder="Type your answer here…" value={a.text ?? ''} onChange={(e) => set(q, { text: e.target.value })} />;
    const t = { NUMBER: 'number', DATE: 'date', TIME: 'time', EMAIL: 'email' }[q.type] || 'text';
    const placeholder = q.type === 'EMAIL' ? 'Enter your email address' : q.type === 'NUMBER' ? 'Enter a number' : q.type === 'DATE' ? 'Choose a date' : q.type === 'TIME' ? 'Choose a time' : 'Type your answer here…';
    const val = q.type === 'NUMBER' ? (a.number ?? '') : q.type === 'DATE' ? (a.date ?? '') : (a.text ?? '');
    return <>{q.type==='NUMBER'&&q.config?.allowDontKnow&&<label className="checkbox-row"><input type="checkbox" checked={a.text==='DONT_KNOW'} onChange={e=>set(q,e.target.checked?{number:null,text:'DONT_KNOW'}:{text:null})}/>Don't know</label>}<input className={q.type==='SHORT'?'short-answer-input':''} type={t} placeholder={placeholder} value={val} onChange={(e) => { const v = e.target.value; set(q, q.type === 'NUMBER' ? { number: v === '' ? null : v, text:null } : q.type === 'DATE' ? { date: v || null } : { text: v }); }} /></>;
  };

  const block = (q, small) => {
    const a = ans[q.id] || {};
    return (
      <div className={'question-block'+(d.readOnly?' is-read-only':'')} key={q.id}>
        <div className="question-context"><span className="dimension-label">{q.dimensionName}</span><span className="eyebrow">{q.externalId || q.id}</span></div>
        <p className={'question-text' + (small ? ' question-text--sm' : '')}>{q.text}{q.mandatory && <em> *</em>}</p>
        {q.helpText && <p className="muted">Evidence: {q.helpText}</p>}
        {widget(q)}
        <div className="comment-field"><label className="field-label">Optional comment</label>
          <textarea className="comment-input" rows={2} placeholder="Add a comment if needed" value={a.remarks ?? ''} onChange={(e) => set(q, { remarks: e.target.value })} />
        </div>
        <div className="question-actions">
          <button className={'question-state-btn question-state-btn--review'+(a.state==='REVIEW'?' is-active':'')} onClick={() => flag(q, 'REVIEW')}><i aria-hidden="true"/>{a.state === 'REVIEW' ? 'Marked for review' : 'Mark for review'}</button>
        </div>
      </div>
    );
  };

  const top = (
    <div className="topbar">
      <div className="brand">assay<i>.</i></div><span className="spacer" />
      <span className={'save-state'+(saved==='Saving'?' is-saving':'')} aria-live="polite"><i aria-hidden="true"/>{saved}</span><span className="eyebrow">{user?.name}</span>
      <button className="link-btn" onClick={logout}>Sign out</button>
    </div>
  );

  if (mode === 'done') return (
    <>{top}<div className="page">
      <span className="eyebrow">Submitted · version {d.versionNo}</span>
      <p className="question-text">Thank you. Your response is submitted.</p>
      {!d.readOnly && <button className="btn-solid" onClick={() => { setMode('ask'); setCur(0); }}>Edit my response</button>}
    </div></>
  );

  const secState = d.sections.map((s) => { const qs = Q.filter((x) => x.sec.id === s.id); return { s, qs, k: qs.filter((x) => has(ans[x.id])).length }; });
  const current = Q[cur];
  const cnt = (k) => Q.filter((q) => st(q) === k).length;

  const progress = (
    <>
      <div className="progress-line"><span className="progress-label">Overall progress</span><span className="progress-count"><b>{done}/{Q.length}</b> answered <i/> <b>{Q.length - done}</b> remaining</span></div>
      <div className="progress-seg">{secState.map(({ s, qs, k }) => (
        <span key={s.id} className={'progress-seg__seg' + (mode === 'ask' && current?.sec.id === s.id ? ' is-current' : '')}><span className="progress-seg__fill" style={{ width: (qs.length ? (k / qs.length) * 100 : 0) + '%' }} /></span>
      ))}</div>
    </>
  );

  const index = (
    <div>
      <p className="eyebrow section-index-label">Sections</p>
      {secState.map(({ s, qs, k }) => (
        <div key={s.id} className={'index__item' + (mode === 'ask' && current?.sec.id === s.id ? ' is-current' : '')}>
          <button className="index__head" onClick={() => { setMode('ask'); setCur(Math.max(0, first(s))); }}>
            <span>{s.code ? s.code + ' · ' : ''}{s.name}</span><span className="index__count"><b>{k}/{qs.length}</b><small>{qs.length - k} left</small></span>
          </button>
          <div className="dot-matrix">{qs.map((q) => <button key={q.id} title={q.externalId} className={'dot-matrix__dot is-' + st(q) + (mode === 'ask' && current?.id === q.id ? ' is-current' : '')} onClick={() => { setMode('ask'); setCur(Q.indexOf(q)); }} />)}</div>
        </div>
      ))}
      <div className="legend">{[['done', 'Answered'], ['review', 'Review'], ['pend', 'Unanswered']].map(([k, n]) => <span key={k}><i className={'is-' + k} />{n} <b>{cnt(k)}</b></span>)}</div>
    </div>
  );

  const next = () => {
    if (all) {
      const qs = Q.filter((x) => x.sec.id === current.sec.id);
      if (qs.some((x) => x.mandatory && !has(ans[x.id]))) { setErr('Answer the required questions (*) first.'); return; }
      setErr(''); const last = qs[qs.length - 1]; const i = Q.indexOf(last) + 1;
      if (i >= Q.length) setMode('review'); else setCur(i);
    } else {
      if (current.mandatory && !has(ans[current.id])) { setErr('This question is required.'); return; }
      setErr('');
      if (cur === Q.length - 1) setMode('review'); else setCur(cur + 1);
    }
    questionScroll.current?.scrollTo({ top: 0, behavior: 'smooth' });
  };
  const back = () => { setErr(''); if (all) { const i = first(current.sec) - 1; if (i >= 0) setCur(first(Q[i].sec)); } else if (cur > 0) setCur(cur - 1); questionScroll.current?.scrollTo({ top: 0, behavior: 'smooth' }); };

  const submit = async () => {
    try { await flush(); await api.post(`/submissions/${id}/submit`); setMode('done'); }
    catch (e) {
      const m = e.response?.data?.missing;
      if (m?.length) { setCur(Math.max(0, Q.findIndex((x) => x.id === m[0].questionId))); setMode('ask'); setErr('Required answers are missing.'); } else toast(msg(e));
    }
  };

  if (mode === 'review') {
    const miss = Q.filter((q) => q.mandatory && !has(ans[q.id])).length;
    return (
      <>{top}<div className="page respondent-page">{progress}
        <div className="resp-layout resp-layout--review" style={{'--resp-index-width':`${indexWidth}px`}}><div className="resp-section-pane">{index}</div>
          <div className="resp-splitter" role="separator" aria-orientation="vertical" aria-label="Resize section list" aria-valuenow={indexWidth} tabIndex={0} onPointerDown={beginIndexResize} onDoubleClick={()=>setIndexWidth(300)} onKeyDown={e=>{if(e.key==='ArrowLeft')setIndexWidth(x=>Math.max(240,x-20));if(e.key==='ArrowRight')setIndexWidth(x=>Math.min(520,x+20));if(e.key==='Home')setIndexWidth(300);}}><span/></div>
          <div className="resp-question-pane resp-question-pane--review">
            <span className="eyebrow">Review</span><p className="question-text">Ready to submit?</p>
            {secState.map(({ s, qs, k }) => <div className="score-row score-row--compact" key={s.id}><span>{s.code ? s.code + ' · ' : ''}{s.name}</span><b className="mono">{k}/{qs.length}</b></div>)}
            <div className="error-text review-error">{miss ? `${miss} required questions are unanswered.` : ''}</div>
            <div className="nav-row"><button className="link-btn" onClick={() => { setMode('ask'); setCur(Q.length - 1); }}>← Back</button><button className="btn-solid" onClick={submit}>Submit response</button></div>
          </div>
        </div>
      </div></>
    );
  }

  const secQs = Q.filter((x) => x.sec.id === current.sec.id);
  return (
     <>{top}<div className="page respondent-page">{progress}
      <div className="resp-layout" style={{'--resp-index-width':`${indexWidth}px`}}><div className="resp-section-pane">{index}</div>
        <div className="resp-splitter" role="separator" aria-orientation="vertical" aria-label="Resize section list" aria-valuenow={indexWidth} tabIndex={0} onPointerDown={beginIndexResize} onDoubleClick={()=>setIndexWidth(300)} onKeyDown={e=>{if(e.key==='ArrowLeft')setIndexWidth(x=>Math.max(240,x-20));if(e.key==='ArrowRight')setIndexWidth(x=>Math.min(520,x+20));if(e.key==='Home')setIndexWidth(300);}}><span/></div>
        <div className="resp-question-pane">
          {d.readOnly && <p className="error-text">This form is closed. Your answers are read-only.</p>}
          <div className="toggle-row">
            <span className="section-progress-label">{current.sec.name} <i/> {secQs.filter((x) => !has(ans[x.id])).length} left</span>
            <div className="show-all-control"><span>Show all questions</span><Switch on={all} onClick={() => !d.readOnly&&setAll(!all)} /></div>
          </div>
          <div className="resp-question-scroll" ref={questionScroll} key={current.sec.id}>
            {all ? secQs.map((q) => block(q, true)) : block(current)}
            <div className="error-text">{err}</div>
            <div className="nav-row">
              <button className="link-btn" disabled={d.readOnly} onClick={back}>← Back</button>
              <button className="btn-solid" disabled={d.readOnly} onClick={next}>{all ? 'Next section →' : cur === Q.length - 1 ? 'Review →' : 'Next →'}</button>
            </div>
          </div>
        </div>
      </div>
    </div></>
  );
}
