import React, { useEffect, useState } from 'react';
import { BrainCircuit, Check, Loader2, Save } from 'lucide-react';
import { getSlideAnalysis, updateSlideAnalysis } from '../../api/intelligence';

export const SlideInspector = ({ presentationId, slideNumber, refreshKey = 0 }) => {
  const [slides, setSlides] = useState([]);
  const [title, setTitle] = useState('');
  const [concepts, setConcepts] = useState([]);
  const [conceptInput, setConceptInput] = useState('');
  const [status, setStatus] = useState('loading');
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!presentationId) return;
    let active = true;
    setStatus('loading');
    getSlideAnalysis(presentationId)
      .then((data) => {
        if (!active) return;
        const next = Array.isArray(data) ? data : data.data || [];
        setSlides(next);
        setStatus('ready');
      })
      .catch(() => active && setStatus('error'));
    return () => { active = false; };
  }, [presentationId, refreshKey]);

  const slide = slides.find((item) => item.slideNumber === slideNumber) || slides[0];
  useEffect(() => {
    setTitle(slide?.title || '');
    setConcepts(slide?.concepts || []);
  }, [slide]);

  const addConcept = () => {
    const value = conceptInput.trim();
    if (value && !concepts.includes(value) && concepts.length < 20) {
      setConcepts([...concepts, value]);
      setConceptInput('');
    }
  };

  const save = async () => {
    if (!slide) return;
    setSaving(true);
    setSaved(false);
    try {
      await updateSlideAnalysis(presentationId, slide.id, { title, concepts });
      setSaved(true);
      setSlides((current) => current.map((item) => item.id === slide.id ? { ...item, title, concepts } : item));
    } finally {
      setSaving(false);
    }
  };

  if (status === 'loading') return <div className="p-4 text-sm text-slate-400">Loading slide analysis…</div>;
  if (status === 'error') return <div className="p-4 text-sm text-rose-400">Slide analysis could not be loaded.</div>;
  if (!slide) return <div className="p-4 text-sm text-slate-400">Analysis is not available yet.</div>;

  const processing = slide.analysisStatus === 'QUEUED' || slide.analysisStatus === 'PROCESSING';
  return (
    <section className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 space-y-4">
      <header className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-white flex items-center gap-2">
          <BrainCircuit className="w-4 h-4 text-indigo-400" /> Slide analysis
        </h3>
        {processing && <span className="text-xs text-amber-400 flex items-center gap-1"><Loader2 className="w-3 h-3 animate-spin" /> processing…</span>}
        {!processing && saved && <span className="text-xs text-emerald-400 flex items-center gap-1"><Check className="w-3 h-3" /> saved</span>}
      </header>
      <label className="block text-xs text-slate-400">
        Title
        <input value={title} onChange={(event) => setTitle(event.target.value)}
          className="mt-1 w-full rounded-lg bg-slate-800 border border-slate-700 px-3 py-2 text-sm text-white" maxLength={500} />
      </label>
      <div>
        <p className="text-xs text-slate-400 mb-2">Key concepts</p>
        <div className="flex flex-wrap gap-2">
          {concepts.map((concept) => (
            <button key={concept} type="button" onClick={() => setConcepts(concepts.filter((item) => item !== concept))}
              className="px-2 py-1 text-xs rounded-full bg-indigo-500/15 text-indigo-300 border border-indigo-500/30">
              {concept} ×
            </button>
          ))}
        </div>
        <div className="flex gap-2 mt-2">
          <input value={conceptInput} onChange={(event) => setConceptInput(event.target.value)}
            onKeyDown={(event) => event.key === 'Enter' && (event.preventDefault(), addConcept())}
            placeholder="Add concept" className="min-w-0 flex-1 rounded-lg bg-slate-800 border border-slate-700 px-3 py-2 text-xs text-white" />
          <button type="button" onClick={addConcept} className="px-3 rounded-lg bg-slate-800 text-xs text-slate-300">Add</button>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-2 text-xs text-slate-400">
        <span>Code: {slide.hasCode ? 'detected' : 'none'}</span><span>Table: {slide.hasTable ? 'detected' : 'none'}</span>
      </div>
      <button type="button" onClick={save} disabled={saving} className="w-full flex justify-center items-center gap-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 py-2 text-xs font-semibold text-white">
        <Save className="w-3.5 h-3.5" /> {saving ? 'Saving…' : 'Save corrections'}
      </button>
    </section>
  );
};
