import React, { useCallback, useEffect, useState } from 'react';
import {
  getMeetingContext,
  getQuestionAnalytics,
  reviewQuestion,
} from '../api/intelligence';

export const MeetingIntelligencePanel = ({ meetingCode }) => {
  const [context, setContext] = useState(null);
  const [analytics, setAnalytics] = useState(null);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    try {
      const [nextContext, nextAnalytics] = await Promise.all([
        getMeetingContext(meetingCode),
        getQuestionAnalytics(meetingCode),
      ]);
      setContext(nextContext);
      setAnalytics(nextAnalytics);
      setError('');
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to load intelligence context');
    }
  }, [meetingCode]);

  useEffect(() => {
    refresh();
    const timer = window.setInterval(refresh, 10000);
    return () => window.clearInterval(timer);
  }, [refresh]);

  const updateStatus = async (questionId, status) => {
    try {
      await reviewQuestion(meetingCode, questionId, status);
      await refresh();
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to update question');
    }
  };

  return (
    <section className="mt-4 rounded-2xl border border-indigo-500/20 bg-indigo-950/20 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-bold text-white">Presenter intelligence</h2>
          <p className="text-[11px] text-slate-400">
            {context?.currentSlide ? `Slide ${context.currentSlide}` : 'No active slide'}
            {context?.presentationTitle ? ` · ${context.presentationTitle}` : ''}
          </p>
        </div>
        <span className="rounded-full bg-indigo-500/15 px-2 py-1 text-[10px] text-indigo-300">
          {context?.openQuestionCount || 0} open
        </span>
      </div>

      {error && <p className="mt-2 text-xs text-rose-300">{error}</p>}
      {context?.currentSlideText && (
        <p className="mt-3 line-clamp-3 rounded-lg bg-slate-950/50 p-2 text-xs text-slate-300">
          {context.currentSlideText}
        </p>
      )}

      <div className="mt-3 space-y-2">
        {(context?.prioritizedQuestions || []).map((question) => (
          <div key={question.id} className="rounded-lg border border-slate-800 bg-slate-900/70 p-2">
            <div className="flex items-center justify-between gap-2">
              <span className="text-[10px] font-semibold text-amber-300">
                Priority {question.priorityScore ?? 0}
              </span>
              <span className="text-[10px] text-slate-500">{question.type}</span>
            </div>
            <p className="mt-1 text-xs text-slate-200">{question.text}</p>
            <div className="mt-2 flex gap-2">
              {question.status === 'OPEN' && (
                <button type="button" onClick={() => updateStatus(question.id, 'SELECTED')} className="text-[10px] text-indigo-300">
                  Select
                </button>
              )}
              {(question.status === 'OPEN' || question.status === 'SELECTED') && (
                <button type="button" onClick={() => updateStatus(question.id, 'ANSWERED')} className="text-[10px] text-emerald-300">
                  Answered
                </button>
              )}
              <button type="button" onClick={() => updateStatus(question.id, 'DISMISSED')} className="text-[10px] text-slate-400">
                Dismiss
              </button>
            </div>
          </div>
        ))}
      </div>

      {analytics && (
        <p className="mt-3 text-[10px] text-slate-500">
          {analytics.total} total · {analytics.duplicates} duplicate · {analytics.answered} answered
        </p>
      )}
    </section>
  );
};
