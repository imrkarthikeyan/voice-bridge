import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { Footer } from '../components/Footer';
import { QrCode, Zap, Radio, Users, ArrowRight, CheckCircle2 } from 'lucide-react';

export const LandingPage = () => {
  const [meetingCode, setMeetingCode] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleJoin = (e) => {
    e.preventDefault();
    if (!meetingCode.trim()) {
      setError('Please enter a 6-character meeting code');
      return;
    }
    const cleanCode = meetingCode.trim().toUpperCase();
    navigate(`/join/${cleanCode}`);
  };

  const steps = [
    { num: '1', title: 'Create Meeting', desc: 'Organizer creates a meeting session in one click.' },
    { num: '2', title: 'Display QR Code', desc: 'Project the QR code on auditorium screens.' },
    { num: '3', title: 'Audience Scans', desc: 'Attendees scan to join without downloading apps.' },
    { num: '4', title: 'Approve Request', desc: 'Organizer approves hand-raise queue requests.' },
    { num: '5', title: 'Speak Live', desc: 'Audience phone streams WebRTC audio to speakers.' },
  ];

  return (
    <div className="min-h-screen flex flex-col bg-slate-950 text-slate-100 selection:bg-indigo-600 selection:text-white">
      <Navbar />

      <main className="flex-1">
        {/* Hero Section */}
        <section className="relative pt-12 pb-20 md:pt-20 md:pb-28 overflow-hidden">
          <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-indigo-600/15 rounded-full blur-[140px] pointer-events-none" />
          <div className="absolute top-1/3 right-10 w-[400px] h-[400px] bg-blue-600/10 rounded-full blur-[120px] pointer-events-none" />

          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
            <div className="text-center max-w-3xl mx-auto">
              <div className="inline-flex items-center space-x-2 px-3.5 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-semibold mb-6">
                <Radio className="w-3.5 h-3.5 animate-pulse" />
                <span>Next-Gen Smart Audience Audio System</span>
              </div>

              <h1 className="text-4xl sm:text-6xl font-extrabold font-heading text-white tracking-tight leading-[1.15]">
                No More Passing Physical Mics.{' '}
                <span className="bg-gradient-to-r from-indigo-400 via-blue-300 to-cyan-400 bg-clip-text text-transparent">
                  Your Phone is the Mic.
                </span>
              </h1>

              <p className="mt-6 text-base sm:text-lg text-slate-300 leading-relaxed max-w-2xl mx-auto">
                Audience members scan a QR code, raise their hand, and after organizer approval, stream crystal-clear live audio directly to auditorium speakers using WebRTC.
              </p>

              {/* Quick Join Box */}
              <div className="mt-10 max-w-md mx-auto p-6 rounded-2xl glass-panel shadow-2xl border border-slate-800">
                <form onSubmit={handleJoin} className="flex flex-col space-y-3">
                  <label className="text-xs font-semibold text-slate-300 uppercase tracking-wider text-left">
                    Join Session as Audience
                  </label>
                  <div className="flex space-x-2">
                    <input
                      type="text"
                      maxLength={12}
                      placeholder="Enter Code (e.g. AB12CD)"
                      value={meetingCode}
                      onChange={(e) => {
                        setMeetingCode(e.target.value);
                        setError('');
                      }}
                      className="flex-1 bg-slate-900 border border-slate-700 focus:border-indigo-500 rounded-xl px-4 py-3 text-white text-sm uppercase font-mono tracking-widest placeholder:text-slate-500 placeholder:normal-case placeholder:tracking-normal focus:outline-none transition-colors"
                    />
                    <button
                      type="submit"
                      className="px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-sm shadow-lg shadow-indigo-600/30 flex items-center space-x-2 transition-all flex-shrink-0"
                    >
                      <span>Join</span>
                      <ArrowRight className="w-4 h-4" />
                    </button>
                  </div>
                  {error && <p className="text-xs text-rose-400 text-left mt-1 font-medium">{error}</p>}
                </form>
              </div>

              {/* Organizer Link */}
              <div className="mt-8 flex flex-wrap items-center justify-center space-x-2 text-xs">
                <span className="text-slate-400">Hosting a seminar, placement talk or lecture?</span>
                <Link
                  to="/login"
                  className="font-semibold text-indigo-400 hover:text-indigo-300 underline underline-offset-4 flex items-center space-x-1"
                >
                  <span>Organizer Dashboard</span>
                  <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
              <div className="mt-3 text-xs">
                <Link to="/audience/account" className="font-semibold text-cyan-400 hover:text-cyan-300 underline underline-offset-4">
                  Audience account
                </Link>
                <span className="ml-2 text-slate-500">optional — anonymous joining still works</span>
              </div>
            </div>
          </div>
        </section>

        {/* How VoiceBridge Works Section */}
        <section className="py-16 bg-slate-900/60 border-y border-slate-800/80">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center max-w-2xl mx-auto mb-12">
              <h2 className="text-2xl sm:text-3xl font-bold font-heading text-white">How VoiceBridge Works</h2>
              <p className="text-slate-400 text-sm mt-2">5 simple steps to transform your auditorium Q&A sessions.</p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
              {steps.map((step) => (
                <div
                  key={step.num}
                  className="p-5 rounded-2xl bg-slate-950 border border-slate-800 flex flex-col justify-between hover:border-slate-700 transition-all"
                >
                  <div className="flex items-center justify-between mb-3">
                    <span className="w-8 h-8 rounded-lg bg-indigo-500/10 text-indigo-400 font-mono font-bold text-sm flex items-center justify-center">
                      #{step.num}
                    </span>
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                  </div>
                  <div>
                    <h3 className="font-bold text-white text-sm font-heading">{step.title}</h3>
                    <p className="text-slate-400 text-xs mt-1 leading-relaxed">{step.desc}</p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Core Features */}
        <section className="py-16">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <div className="p-6 rounded-2xl bg-slate-900/70 border border-slate-800 flex flex-col space-y-3">
                <div className="w-10 h-10 rounded-xl bg-indigo-500/10 text-indigo-400 flex items-center justify-center">
                  <QrCode className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white font-heading">Instant Mobile Join</h3>
                <p className="text-slate-400 text-xs leading-relaxed">
                  Attendees scan the QR code to join immediately on mobile browsers with zero app installation required.
                </p>
              </div>

              <div className="p-6 rounded-2xl bg-slate-900/70 border border-slate-800 flex flex-col space-y-3">
                <div className="w-10 h-10 rounded-xl bg-blue-500/10 text-blue-400 flex items-center justify-center">
                  <Users className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white font-heading">FCFS Queue & Moderation</h3>
                <p className="text-slate-400 text-xs leading-relaxed">
                  Organizers approve speaking requests in order. Only one participant speaks at a time to keep discussions clear.
                </p>
              </div>

              <div className="p-6 rounded-2xl bg-slate-900/70 border border-slate-800 flex flex-col space-y-3">
                <div className="w-10 h-10 rounded-xl bg-cyan-500/10 text-cyan-400 flex items-center justify-center">
                  <Zap className="w-5 h-5" />
                </div>
                <h3 className="text-lg font-bold text-white font-heading">WebRTC HD Audio</h3>
                <p className="text-slate-400 text-xs leading-relaxed">
                  Ultra-low latency peer-to-peer audio streaming with built-in noise suppression and echo cancellation.
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
};
