import React, { useState } from 'react';
import { Send } from 'lucide-react';
import { createQuestion } from '../api/intelligence';

export const QuestionComposer = ({ meetingCode, onCreated }) => {
  const [text, setText] = useState('');
  const [status, setStatus] = useState('');

  const submit = async (event) => {
    event.preventDefault();
    if (!text.trim()) return;
    try {
      setStatus('Sending...');
      const question = await createQuestion(meetingCode, text.trim());
      setText('');
      setStatus(`Question submitted as ${question.type || 'question'}`);
      onCreated?.(question);
    } catch (error) {
      setStatus(error.response?.data?.message || 'Could not submit question');
    }
  };

  return (
    <form onSubmit={submit} className="space-y-2">
      <textarea
        value={text}
        onChange={(event) => setText(event.target.value)}
        maxLength={6000}
        rows={3}
        placeholder="Ask a question about the presentation"
        className="w-full rounded-xl border border-slate-700 bg-slate-900 p-3 text-sm text-white"
      />
      <div className="flex items-center justify-between">
        <span className="text-xs text-slate-500">{status}</span>
        <button type="submit" className="flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white">
          <Send className="h-3.5 w-3.5" /> Submit question
        </button>
      </div>
    </form>
  );
};
