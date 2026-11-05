import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  deleteAudienceAccount, deleteAudienceProfile, exportAudienceProfile, getAudienceAccount,
  linkAudienceParticipant, loginAudience, registerAudience, startGithubAudienceLogin,
} from '../api/audience';

const saveSession = (data) => {
  localStorage.setItem('vb_token', data.token);
  localStorage.setItem('vb_audience', JSON.stringify(data.audience));
};

export const AudienceAccountPage = () => {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ displayName: '', email: '', password: '' });
  const [account, setAccount] = useState(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    getAudienceAccount().then(setAccount).catch(() => {});
  }, []);

  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      const result = mode === 'login'
        ? await loginAudience({ email: form.email, password: form.password })
        : await registerAudience(form);
      saveSession(result); setAccount(result.audience); setMessage('Signed in successfully.');
    } catch (error) { setMessage(error.response?.data?.message || 'Could not complete account request.'); }
    finally { setBusy(false); }
  };

  const action = async (callback, success) => {
    setBusy(true); setMessage('');
    try { await callback(); setMessage(success); if (success === 'Account deleted.') { localStorage.removeItem('vb_token'); localStorage.removeItem('vb_audience'); setAccount(null); } }
    catch (error) { setMessage(error.response?.data?.message || 'Request failed.'); }
    finally { setBusy(false); }
  };

  if (account) return (
    <main className="min-h-screen bg-slate-950 p-6 text-slate-100">
      <div className="mx-auto max-w-xl rounded-2xl border border-slate-800 bg-slate-900 p-6">
        <h1 className="text-2xl font-bold text-white">Audience account</h1>
        <p className="mt-2 text-sm text-slate-300">{account.displayName} · {account.email}</p>
        <div className="mt-6 grid gap-3">
          <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold" disabled={busy} onClick={() => action(() => linkAudienceParticipant(localStorage.getItem('vb_session_token')), 'Participant linked.')}>Link current anonymous participant</button>
          <button className="rounded-lg border border-slate-700 px-4 py-2 text-sm" disabled={busy} onClick={async () => { const data = await exportAudienceProfile(); const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'voicebridge-profile.json'; anchor.click(); URL.revokeObjectURL(url); setMessage('Profile exported.'); }}>Export profile</button>
          <button className="rounded-lg border border-amber-700 px-4 py-2 text-sm text-amber-300" disabled={busy} onClick={() => action(deleteAudienceProfile, 'Profile deleted.')}>Delete profile</button>
          <button className="rounded-lg border border-rose-700 px-4 py-2 text-sm text-rose-300" disabled={busy} onClick={() => { if (window.confirm('Delete this audience account?')) action(deleteAudienceAccount, 'Account deleted.'); }}>Delete account</button>
        </div>
        <p className="mt-4 text-xs text-slate-400">{message}</p>
        <Link className="mt-4 inline-block text-sm text-blue-400" to="/">Return home</Link>
      </div>
    </main>
  );

  return (
    <main className="min-h-screen bg-slate-950 p-6 text-slate-100">
      <div className="mx-auto max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-6">
        <h1 className="text-2xl font-bold text-white">Audience account</h1>
        <p className="mt-2 text-xs text-slate-400">Anonymous meeting joining remains available without an account.</p>
        <div className="mt-5 flex gap-2 text-sm"><button className="text-blue-400" onClick={() => setMode('login')}>Sign in</button><button className="text-blue-400" onClick={() => setMode('register')}>Register</button></div>
        <form onSubmit={submit} className="mt-4 grid gap-3">
          {mode === 'register' && <input required placeholder="Display name" className="rounded-lg bg-slate-950 p-3" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} />}
          <input required type="email" placeholder="Email" className="rounded-lg bg-slate-950 p-3" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <input required minLength={8} type="password" placeholder="Password" className="rounded-lg bg-slate-950 p-3" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <button disabled={busy} className="rounded-lg bg-blue-600 p-3 font-semibold">{mode === 'login' ? 'Sign in' : 'Create account'}</button>
        </form>
        <button disabled={busy} onClick={startGithubAudienceLogin} className="mt-3 w-full rounded-lg border border-slate-700 p-3 text-sm">Continue with GitHub</button>
        <p className="mt-4 text-xs text-slate-400">{message}</p>
        <button className="mt-3 text-sm text-blue-400" onClick={() => navigate(-1)}>Back</button>
      </div>
    </main>
  );
};

export const AudienceOAuthCallbackPage = () => {
  const navigate = useNavigate();
  useEffect(() => {
    const token = new URLSearchParams(window.location.hash.slice(1)).get('token');
    if (token) { localStorage.setItem('vb_token', token); navigate('/audience/account', { replace: true }); }
    else navigate('/audience/account?oauth=failed', { replace: true });
  }, [navigate]);
  return <main className="min-h-screen bg-slate-950 p-8 text-white">Completing GitHub sign in…</main>;
};
