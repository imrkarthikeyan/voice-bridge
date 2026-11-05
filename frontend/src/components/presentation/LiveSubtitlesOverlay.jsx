import React, { useState } from 'react';
import { Volume2, Mic, Eye, EyeOff, Sparkles } from 'lucide-react';

export const LiveSubtitlesOverlay = ({ speakerName, transcript, translation, slideNumber, isLive }) => {
  const [isVisible, setIsVisible] = useState(true);

  if (!isLive || !speakerName) return null;

  return (
    <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-30 max-w-3xl w-[92%] transition-all duration-300">
      <div className="relative group rounded-2xl bg-slate-950/85 backdrop-blur-md border border-indigo-500/30 p-4 shadow-2xl shadow-indigo-950/50 text-slate-100 overflow-hidden">
        {/* Animated Top Border Accent Line */}
        <div className="absolute top-0 left-0 right-0 h-0.5 bg-gradient-to-r from-indigo-500 via-purple-500 to-emerald-400 animate-pulse" />

        {/* Header Bar: Speaker Info & Controls */}
        <div className="flex items-center justify-between pb-2 mb-2 border-b border-slate-800/60">
          <div className="flex items-center gap-2.5">
            <span className="relative flex h-3 w-3">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500"></span>
            </span>

            <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-semibold">
              <Mic className="w-3.5 h-3.5 text-indigo-400 animate-pulse" />
              <span>Live Speaker: <strong className="text-white font-bold">{speakerName}</strong></span>
            </div>

            <div className="hidden sm:flex items-center gap-1 text-[11px] text-purple-300 bg-purple-500/10 px-2 py-0.5 rounded-full border border-purple-500/20">
              <Sparkles className="w-3 h-3 text-purple-400" />
              <span>AI Subtitles</span>
            </div>
            {slideNumber && (
              <span className="text-[11px] text-emerald-300 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20">
                Slide {slideNumber}
              </span>
            )}
          </div>

          <button
            onClick={() => setIsVisible((prev) => !prev)}
            className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors text-xs flex items-center gap-1"
            title={isVisible ? 'Hide subtitles' : 'Show subtitles'}
          >
            {isVisible ? (
              <>
                <EyeOff className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Hide</span>
              </>
            ) : (
              <>
                <Eye className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Subtitles</span>
              </>
            )}
          </button>
        </div>

        {/* Live Subtitle Content */}
        {isVisible && (
          <div className="min-h-[2.5rem] flex items-center">
            {transcript ? (
              <div>
                <p className="text-base sm:text-lg font-medium text-slate-100 leading-relaxed tracking-wide transition-all animate-fade-in">
                  "{transcript}"
                </p>
                {translation?.text && (
                  <p className="mt-1 text-sm text-emerald-200/90 transition-all animate-fade-in">
                    {translation.text}
                  </p>
                )}
              </div>
            ) : (
              <p className="text-xs italic text-slate-400 flex items-center gap-2">
                <Volume2 className="w-4 h-4 animate-bounce text-indigo-400" />
                <span>Listening for audience speech...</span>
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
