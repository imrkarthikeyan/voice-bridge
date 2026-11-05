import React, { useEffect, useRef, useState, useCallback } from 'react';
import { getSlideImageUrl, changeSlide, startPresentation, stopPresentation } from '../../api/presentations';
import { PresentationControls } from './PresentationControls';
import { LiveSubtitlesOverlay } from './LiveSubtitlesOverlay';
import { Loader2, AlertTriangle, Presentation } from 'lucide-react';

export const PresentationViewer = ({
  presentation,
  session,
  isOrganizer = true,
  onSessionChange,
  activeSpeakerName,
  transcript,
  transcriptTranslation,
  transcriptSlideNumber,
  isLiveSpeaker,
}) => {
  const [currentSlide, setCurrentSlide] = useState(session?.currentSlide || presentation?.currentSlide || 1);
  const [isPresenting, setIsPresenting] = useState(session?.presenting || false);
  const [isLoadingImage, setIsLoadingImage] = useState(true);
  const [imageError, setImageError] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const containerRef = useRef(null);
  const totalSlides = presentation?.totalSlides || 1;
  const presentationId = presentation?.id;

  // Sync internal state when props change
  useEffect(() => {
    if (session) {
      if (session.currentSlide) setCurrentSlide(session.currentSlide);
      setIsPresenting(!!session.presenting);
    } else if (presentation) {
      if (presentation.currentSlide) setCurrentSlide(presentation.currentSlide);
    }
  }, [session, presentation]);

  // Reset image status when slide changes
  useEffect(() => {
    setIsLoadingImage(true);
    setImageError(false);
  }, [presentationId, currentSlide]);

  const handleNavigate = useCallback(
    async (targetSlide) => {
      if (!isOrganizer || !presentationId) return;
      if (targetSlide < 1 || targetSlide > totalSlides) return;

      try {
        setCurrentSlide(targetSlide);
        const updatedSession = await changeSlide(presentationId, targetSlide);
        onSessionChange?.(updatedSession);
      } catch (err) {
        console.error('Failed to change slide:', err);
      }
    },
    [isOrganizer, presentationId, totalSlides, onSessionChange]
  );

  const handleFirst = useCallback(() => handleNavigate(1), [handleNavigate]);
  const handlePrev = useCallback(() => handleNavigate(currentSlide - 1), [handleNavigate, currentSlide]);
  const handleNext = useCallback(() => handleNavigate(currentSlide + 1), [handleNavigate, currentSlide]);
  const handleLast = useCallback(() => handleNavigate(totalSlides), [handleNavigate, totalSlides]);
  const handleJump = useCallback((slideNum) => handleNavigate(slideNum), [handleNavigate]);

  const handleStart = async () => {
    if (!isOrganizer || !presentationId) return;
    try {
      const updatedSession = await startPresentation(presentationId);
      setIsPresenting(true);
      onSessionChange?.(updatedSession);
    } catch (err) {
      console.error('Failed to start presentation:', err);
    }
  };

  const handleStop = async () => {
    if (!isOrganizer || !presentationId) return;
    try {
      const updatedSession = await stopPresentation(presentationId);
      setIsPresenting(false);
      onSessionChange?.(updatedSession);
    } catch (err) {
      console.error('Failed to stop presentation:', err);
    }
  };

  // Fullscreen management
  const exitFullscreen = useCallback(() => {
    if (document.fullscreenElement) {
      document.exitFullscreen?.().catch((err) => console.warn('Exit fullscreen error:', err));
    }
    setIsFullscreen(false);
  }, []);

  // Keyboard navigation listener
  useEffect(() => {
    const handleKeyDown = (e) => {
      // Ignore keypress if typing in input field
      const activeTag = document.activeElement?.tagName?.toLowerCase();
      if (activeTag === 'input' || activeTag === 'textarea' || activeTag === 'select') {
        return;
      }

      if (!isOrganizer) return;

      switch (e.key) {
        case 'ArrowRight':
        case 'PageDown':
        case ' ':
          e.preventDefault();
          handleNext();
          break;
        case 'ArrowLeft':
        case 'PageUp':
          e.preventDefault();
          handlePrev();
          break;
        case 'Home':
          e.preventDefault();
          handleFirst();
          break;
        case 'End':
          e.preventDefault();
          handleLast();
          break;
        case 'Escape':
          if (isFullscreen) {
            exitFullscreen();
          }
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOrganizer, handleNext, handlePrev, handleFirst, handleLast, isFullscreen, exitFullscreen]);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      containerRef.current?.requestFullscreen?.().catch((err) => {
        console.warn('Fullscreen error:', err);
      });
      setIsFullscreen(true);
    } else {
      exitFullscreen();
    }
  };

  useEffect(() => {
    const handleFSChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener('fullscreenchange', handleFSChange);
    return () => document.removeEventListener('fullscreenchange', handleFSChange);
  }, []);

  if (!presentation) {
    return (
      <div className="flex flex-col items-center justify-center p-12 bg-slate-900 border border-slate-800 rounded-2xl text-center">
        <Presentation className="w-12 h-12 text-slate-600 mb-3" />
        <p className="text-slate-400 font-medium">No presentation active</p>
      </div>
    );
  }

  const slideImageUrl = getSlideImageUrl(presentationId, currentSlide);

  return (
    <div
      ref={containerRef}
      className={`flex flex-col bg-slate-950 rounded-2xl border border-slate-800 overflow-hidden shadow-2xl transition-all ${isFullscreen ? 'fixed inset-0 z-50 rounded-none border-none' : 'w-full'
        }`}
    >
      {/* Slide Canvas Header (Title) */}
      {!isFullscreen && (
        <div className="flex items-center justify-between px-5 py-3 bg-slate-900 border-b border-slate-800">
          <div className="flex items-center space-x-3 truncate">
            <span className="font-semibold text-white text-sm truncate">{presentation.fileName}</span>
            {isPresenting && (
              <span className="px-2 py-0.5 text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-full">
                PRESENTING
              </span>
            )}
          </div>
          <div className="text-xs text-slate-400 font-medium">
            Slide {currentSlide} of {totalSlides}
          </div>
        </div>
      )}

      {/* Main Slide Viewer Canvas */}
      <div className="relative flex-1 flex items-center justify-center min-h-[340px] sm:min-h-[460px] bg-slate-950 p-4 select-none overflow-hidden">
        {isLoadingImage && (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-950/80 z-10">
            <div className="flex flex-col items-center space-y-2">
              <Loader2 className="w-8 h-8 text-indigo-400 animate-spin" />
              <span className="text-xs text-slate-400 font-medium">Loading slide {currentSlide}...</span>
            </div>
          </div>
        )}

        {imageError ? (
          <div className="flex flex-col items-center space-y-2 text-rose-400 p-6 text-center">
            <AlertTriangle className="w-10 h-10" />
            <span className="font-medium text-sm">Failed to load slide image</span>
            <button
              type="button"
              onClick={() => {
                setIsLoadingImage(true);
                setImageError(false);
              }}
              className="text-xs text-indigo-400 hover:underline pt-2"
            >
              Retry
            </button>
          </div>
        ) : (
          <img
            src={slideImageUrl}
            alt={`Slide ${currentSlide}`}
            onLoad={() => setIsLoadingImage(false)}
            onError={() => {
              setIsLoadingImage(false);
              setImageError(true);
            }}
            className="max-h-[75vh] w-auto max-w-full object-contain rounded-lg shadow-2xl transition-all duration-200"
          />
        )}

        {/* Live Subtitles / Speech-to-Text Overlay */}
        <LiveSubtitlesOverlay
          speakerName={activeSpeakerName}
          transcript={transcript}
          translation={transcriptTranslation}
          slideNumber={transcriptSlideNumber}
          isLive={isLiveSpeaker}
        />
      </div>

      {/* Slide Navigation Controls */}
      <div className="p-3 bg-slate-900/90 border-t border-slate-800">
        <PresentationControls
          currentSlide={currentSlide}
          totalSlides={totalSlides}
          isPresenting={isPresenting}
          isFullscreen={isFullscreen}
          isOrganizer={isOrganizer}
          onFirstSlide={handleFirst}
          onPreviousSlide={handlePrev}
          onNextSlide={handleNext}
          onLastSlide={handleLast}
          onJumpToSlide={handleJump}
          onStartPresentation={handleStart}
          onStopPresentation={handleStop}
          onToggleFullscreen={toggleFullscreen}
        />
      </div>
    </div>
  );
};
