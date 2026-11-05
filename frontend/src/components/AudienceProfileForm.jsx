import React, { useEffect, useState } from 'react';
import { getMyProfile, updateMyProfile } from '../api/profiles';

const visibilityOptions = ['NONE', 'PRESENTER', 'PARTICIPANTS'];

export const AudienceProfileForm = () => {
  const [profile, setProfile] = useState(null);
  const [status, setStatus] = useState('');

  useEffect(() => {
    getMyProfile().then(setProfile).catch(() => setStatus('Profile is unavailable for this session.'));
  }, []);

  if (!profile) return status ? <p className="text-xs text-slate-500">{status}</p> : null;

  const update = (field, value) => setProfile((current) => ({ ...current, [field]: value }));
  const save = async () => {
    try {
      setStatus('Saving...');
      await updateMyProfile(profile);
      setStatus('Saved. Sharing applies only to this meeting.');
    } catch (error) {
      setStatus(error.response?.data?.message || 'Could not save profile');
    }
  };

  return (
    <section className="mt-6 rounded-2xl border border-slate-800 bg-slate-900/70 p-5 text-left">
      <h3 className="text-sm font-bold text-white">Optional audience profile</h3>
      <p className="mt-1 text-xs text-slate-400">Your profile is private unless you explicitly choose a meeting audience.</p>
      <div className="mt-4 grid gap-3">
        <input value={profile.headline || ''} onChange={(e) => update('headline', e.target.value)} placeholder="Headline" className="rounded-lg border border-slate-700 bg-slate-950 p-2 text-sm text-white" />
        <textarea value={profile.bio || ''} onChange={(e) => update('bio', e.target.value)} placeholder="Short bio" rows={2} className="rounded-lg border border-slate-700 bg-slate-950 p-2 text-sm text-white" />
        <input value={profile.skills || ''} onChange={(e) => update('skills', e.target.value)} placeholder="Skills (comma separated)" className="rounded-lg border border-slate-700 bg-slate-950 p-2 text-sm text-white" />
        <input value={profile.githubUrl || ''} onChange={(e) => update('githubUrl', e.target.value)} placeholder="GitHub URL (unverified until OAuth)" className="rounded-lg border border-slate-700 bg-slate-950 p-2 text-sm text-white" />
        <label className="text-xs text-slate-400">
          Bio visibility
          <select value={profile.bioVisibility} onChange={(e) => update('bioVisibility', e.target.value)} className="ml-2 rounded border border-slate-700 bg-slate-950 p-1 text-white">
            {visibilityOptions.map((option) => <option key={option}>{option}</option>)}
          </select>
        </label>
        <label className="text-xs text-slate-400">
          Links visibility
          <select value={profile.linksVisibility} onChange={(e) => update('linksVisibility', e.target.value)} className="ml-2 rounded border border-slate-700 bg-slate-950 p-1 text-white">
            {visibilityOptions.map((option) => <option key={option}>{option}</option>)}
          </select>
        </label>
        <div className="flex items-center justify-between">
          <span className="text-xs text-slate-500">{status}</span>
          <button type="button" onClick={save} className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white">Save profile</button>
        </div>
      </div>
    </section>
  );
};
