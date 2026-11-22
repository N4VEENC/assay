import { Fragment, useEffect, useState } from 'react';
import { useNavigate, useParams, Link } from 'react-router-dom';
import { api, msg } from '../api';
import { AdminBar, DD, fmt, toast } from '../components.jsx';

export function Responses() {
  const { id } = useParams();
  const nav = useNavigate();
  const [d, setD] = useState(null);
  const [tab,setTab]=useState('respondents'),[summary,setSummary]=useState(null),[scores,setScores]=useState(null),[section,setSection]=useState('all'),[sectionOptions,setSectionOptions]=useState([]),[expanded,setExpanded]=useState(null),[answers,setAnswers]=useState({}),[versionId,setVersionId]=useState(''),[respondentVersion,setRespondentVersion]=useState('all');
  useEffect(() => { api.get(`/forms/${id}/responses`).then((r) => {setD(r.data);setVersionId(String(r.data.currentVersionId));}).catch((e) => toast(msg(e))); }, [id]);
  useEffect(()=>{if(tab==='questions'&&versionId)api.get(`/forms/${id}/questions/summary`,{params:{versionId,...(section==='all'?{}:{sectionId:section})}}).then(r=>{setSummary(r.data);if(section==='all')setSectionOptions(Array.from(new Map(r.data.filter(q=>q.sectionId).map(q=>[String(q.sectionId),{value:String(q.sectionId),label:`${q.sectionCode ? q.sectionCode+' · ' : ''}${q.sectionName}`}])).values()));}).catch(e=>toast(msg(e)));if(tab==='scores'&&versionId)api.get(`/forms/${id}/scores`,{params:{versionId}}).then(r=>setScores(r.data)).catch(e=>toast(msg(e)));},[id,tab,section,versionId]);
  const downloadAll=async(format)=>{try{const r=await api.get(`/forms/${id}/export`,{params:{format,...(format==='wide'&&versionId?{versionId}:{})},responseType:'blob'});const a=document.createElement('a');a.href=URL.createObjectURL(r.data);a.download=`form-${id}-responses-${format}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);}catch(e){toast(msg(e));}};
  const toggleQuestion=async(qid)=>{if(expanded===qid){setExpanded(null);return;}setExpanded(qid);if(!answers[qid])try{const r=await api.get(`/forms/${id}/questions/${qid}/answers`,{params:{versionId}});setAnswers(x=>({...x,[qid]:r.data}));}catch(e){toast(msg(e));}};
  const fileName=(format)=>downloadAll(format);
  const respondentRows=d?.respondents.filter(r=>respondentVersion==='all'||String(r.versionId)===respondentVersion)||[];
  return (
    <>
      <AdminBar id={id} active="responses" />
      <div className="page">
        <div className="toolbar responses-toolbar"><div className="seg-tabs"><button className={tab==='respondents'?'is-active':''} onClick={()=>setTab('respondents')}>Respondents</button><button className={tab==='questions'?'is-active':''} onClick={()=>setTab('questions')}>By question</button><button className={tab==='scores'?'is-active':''} onClick={()=>setTab('scores')}>Scores</button></div><span className="spacer"/><button className="link-btn" onClick={()=>fileName('wide')}>Export wide ↓</button><button className="link-btn" onClick={()=>fileName('long')}>Export long ↓</button></div>
        {tab==='respondents'&&d&&<div className="responses-filter"><span className="eyebrow">Respondent version</span><DD value={respondentVersion} items={[{value:'all',label:'All versions'},...d.versions.map(v=>({value:String(v.id),label:`v${v.versionNo} · ${v.status.toLowerCase()}`}))]} onChange={setRespondentVersion}/></div>}
        {tab!=='respondents'&&d&&<div className="responses-filter"><span className="eyebrow">Form version</span><DD value={versionId} items={d.versions.map(v=>({value:String(v.id),label:`v${v.versionNo} · ${v.status.toLowerCase()}`}))} onChange={v=>{setVersionId(v);setSection('all');setSummary(null);setScores(null);setExpanded(null);setAnswers({});}}/></div>}
        {tab==='respondents'&&<>
        <table className="table">
          <thead><tr><th>Respondent</th><th>Status</th><th>Progress</th><th>Score</th><th>Submitted</th><th></th></tr></thead>
          <tbody>
            {!d && <tr><td className="muted" colSpan="6">Loading</td></tr>}
            {d&&respondentRows.length === 0 && <tr><td className="muted" colSpan="6">No responses for this version.</td></tr>}
            {respondentRows.map((r) => (
              <tr key={r.submissionId} className="is-click" onClick={() => nav(`/admin/forms/${id}/responses/${r.submissionId}`)}>
                <td><b>{r.name}</b><br /><span className="muted mono">{r.email} · v{r.versionNo}</span></td>
                <td><i className={'status-dot' + (r.status === 'SUBMITTED' ? ' status-dot--ok' : '')} />{r.status === 'SUBMITTED' ? 'Submitted' : 'Draft'}</td>
                <td className="num">{r.answeredCount}/{r.totalCount} · {r.progressPct}%<div className="line-bar"><span className="line-bar__fill" style={{ width: r.progressPct + '%' }} /></div></td>
                <td className="num">{r.score ?? '—'}</td>
                <td className="num">{fmt(r.submittedAt)}</td>
                <td className="link-btn">View →</td>
              </tr>
            ))}
          </tbody>
        </table></>}
        {tab==='questions'&&<>
          <div className="responses-filter"><span className="eyebrow">Section</span><DD value={section} items={[{value:'all',label:'All sections'},...sectionOptions]} onChange={setSection}/></div>
          <table className="table"><thead><tr><th>Question</th><th>Answered</th><th>Distribution</th><th>Average</th></tr></thead><tbody>{!summary&&<tr><td colSpan="4" className="muted">Loading</td></tr>}{summary?.map(q=><Fragment key={q.questionId}><tr className="is-click" onClick={()=>toggleQuestion(q.questionId)}><td>{q.externalId||`Q${q.questionId}`} · {q.text}</td><td className="num">{q.answeredCount}</td><td>{q.distribution?.length?<><div className="stack-bar">{q.distribution.map((x,i)=><span key={x.label} className={'stack-bar__seg '+(i===0?'stack-bar__seg--ok':'stack-bar__seg--warn')} style={{width:x.pct+'%'}} title={`${x.label}: ${x.count}`}/>)}</div><span className="muted">{q.distribution.map(x=>`${x.label} ${x.count}`).join(' · ')}</span></>:'—'}</td><td className="num">{q.avgScore??'—'}</td></tr>{expanded===q.questionId&&<tr><td colSpan="4"><table className="table nested-table"><thead><tr><th>Respondent</th><th>Status</th><th>Answer</th><th>Score</th><th>Comment</th></tr></thead><tbody>{!answers[q.questionId]?<tr><td colSpan="5" className="muted">Loading</td></tr>:answers[q.questionId].map(a=><tr key={a.submissionId}><td>{a.name}<br/><span className="muted mono">{a.email}</span></td><td>{a.status}</td><td>{a.answerText??'—'}</td><td>{a.score??'—'}</td><td>{a.remarks??''}</td></tr>)}</tbody></table></td></tr>}</Fragment>)}</tbody></table>
        </>}
        {tab==='scores'&&<div className="scores-view">{!scores?<p className="muted">Loading</p>:<><div className="kv"><div><span className="eyebrow">Overall</span><b>{scores.overall??'—'}</b></div><div><span className="eyebrow">Target</span><b>{scores.target}</b></div></div>{scores.sections.map(s=><section key={s.id}><h3 className="eyebrow rule-title">{s.code} · {s.name}</h3>{s.dimensions.map(x=>{const pos=x.score==null?null:Math.max(0,Math.min(100,((x.score-1)/3)*100));const target=Math.max(0,Math.min(100,((scores.target-1)/3)*100));const color=x.score==null?'':x.score>=scores.target?'':x.score>=scores.target-1?' score-dot--warn':' score-dot--bad';return <div className="score-row" key={x.id}><span>{x.name}<br/><span className="muted">{x.scoredQuestions} scored questions</span></span><div className="score-track"><i className="score-target" style={{left:target+'%'}}/>{pos!==null&&<i className={'score-dot'+color} style={{left:pos+'%'}}/>}</div><b className="mono">{x.score??'—'}</b></div>})}</section>)}<div className="heat-strip"><h3 className="eyebrow rule-title">Question scores</h3>{scores.heat.map(x=><div className="heat-row" key={x.questionId}><span className="mono">{x.externalId||`Q${x.questionId}`}</span><div className="heat-row__cells"><i className="heat-cell" title={x.avgScore==null?'Unscored':`Average ${x.avgScore}`} style={x.avgScore==null?{}:{backgroundColor:x.avgScore>=scores.target?'var(--c-accent)':x.avgScore>=scores.target-1?'var(--c-warn)':'var(--c-bad)'}}/></div></div>)}</div></>}</div>}
      </div>
    </>
  );
}

export function Person() {
  const { id, sid } = useParams();
  const [d, setD] = useState(null);
  useEffect(() => { api.get(`/forms/${id}/submissions/${sid}`).then((r) => setD(r.data)).catch((e) => toast(msg(e))); }, [id, sid]);
  if (!d) return <><AdminBar id={id} active="responses" /><div className="page muted">Loading</div></>;
  const s = d.submission;
  const download = async () => { try { const r=await api.get(`/forms/${id}/submissions/${sid}/export`,{responseType:'blob'});const a=document.createElement('a');a.href=URL.createObjectURL(r.data);a.download=s.name.toLowerCase().replace(/\s+/g,'-')+'-response.csv';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000); } catch(e) { toast(msg(e)); } };
  return (
    <>
      <AdminBar id={id} active="responses" />
      <div className="page">
        <div className="toolbar"><Link className="link-btn" to={`/admin/forms/${id}/responses`}>← All responses</Link><button className="btn-solid" onClick={download}>Download CSV ↓</button></div>
        <h2 className="h1">{s.name}</h2>
        <div className="kv">
          {[['Status', s.status], ['Answered', `${s.answeredCount}/${s.totalCount}`], ['Score', s.score ?? '—'], ['Version', 'v' + s.versionNo], ['Submitted', fmt(s.submittedAt)]].map(([a, b]) => <div key={a}><span className="eyebrow">{a}</span><b>{b}</b></div>)}
        </div>
        {d.sections.map((sec) => (
          <div key={sec.id}>
            <h3 className="eyebrow rule-title person-section-title">{sec.code} · {sec.name}</h3>
            <table className="table"><tbody>
              {sec.questions.map((q) => (
                <tr key={q.id}>
                  <td className="mono person-qid">{q.externalId || q.id}</td>
                  <td>{q.text}<br /><span className="muted detail-dimension">{q.dimensionName}</span></td>
                  <td className={q.answerText ? 'answer-cell' : 'answer-cell muted'}>{q.answerText ?? '—'}{q.remarks && <div className="muted answer-remarks">{q.remarks}</div>}</td>
                  <td className="num person-score">{q.score ?? ''}</td>
                </tr>
              ))}
            </tbody></table>
          </div>
        ))}
      </div>
    </>
  );
}
