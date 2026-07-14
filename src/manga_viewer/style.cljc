(ns manga-viewer.style
  "CSS for the manga-viewer views (ADR-2607070500). One plain string the
  consumer inlines ([:style (css)]) or writes to a stylesheet. Themed through
  CSS custom properties; defaults mirror the manga.gftd.ai dark reader.")

(def ^:private root
  ":root{
  --manga-viewer-bg:#0c0e14;
  --manga-viewer-stage:#070810;
  --manga-viewer-surface:#151926;
  --manga-viewer-border:#232838;
  --manga-viewer-border-hover:#3a64b0;
  --manga-viewer-text:#e8eaf0;
  --manga-viewer-muted:#9aa3b8;
  --manga-viewer-accent:#274062;
}")

(def ^:private grid
  ".manga-viewer__grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:18px}
.manga-viewer__card{display:flex;flex-direction:column;background:var(--manga-viewer-surface);border:1px solid var(--manga-viewer-border);border-radius:14px;overflow:hidden;color:var(--manga-viewer-text);text-decoration:none;transition:border-color .15s,transform .15s}
.manga-viewer__card:hover{border-color:var(--manga-viewer-border-hover);transform:translateY(-2px)}
.manga-viewer__thumb{aspect-ratio:3/4;background:#000;overflow:hidden}
.manga-viewer__thumb img{width:100%;height:100%;object-fit:cover;display:block}
.manga-viewer__card-body{padding:14px 16px 16px}
.manga-viewer__card-title{margin:0 0 6px;font-size:16px;font-weight:700}
.manga-viewer__card-meta{color:var(--manga-viewer-muted);font-size:12.5px}
.manga-viewer__tags{margin-top:10px;display:flex;flex-wrap:wrap;gap:6px}
.manga-viewer__tag{font-size:11px;color:var(--manga-viewer-text);opacity:.85;background:var(--manga-viewer-bg);border:1px solid var(--manga-viewer-border);border-radius:999px;padding:2px 9px}")

(def ^:private reader
  ".manga-viewer__reader{display:flex;flex-direction:column;height:100%;min-height:0;background:var(--manga-viewer-stage);color:var(--manga-viewer-text)}
.manga-viewer__bar{display:flex;align-items:center;gap:10px;padding:10px 14px;border-bottom:1px solid var(--manga-viewer-border);background:var(--manga-viewer-bg);flex:none}
.manga-viewer__back{color:var(--manga-viewer-text);text-decoration:none;border:1px solid var(--manga-viewer-border);border-radius:8px;padding:5px 10px;font-size:13px;line-height:1}
.manga-viewer__back:hover{border-color:var(--manga-viewer-border-hover)}
.manga-viewer__title{margin:0;font-size:15px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:40vw}
.manga-viewer__meta{color:var(--manga-viewer-muted);font-size:12px;white-space:nowrap}
.manga-viewer__spacer{flex:1}
.manga-viewer__mode-btn{background:var(--manga-viewer-surface);border:1px solid var(--manga-viewer-border);color:var(--manga-viewer-text);border-radius:8px;padding:6px 10px;font-size:13px;cursor:pointer;line-height:1}
.manga-viewer__mode-btn:hover{border-color:var(--manga-viewer-border-hover)}
.manga-viewer__mode-btn.is-on{background:var(--manga-viewer-accent);border-color:var(--manga-viewer-border-hover)}
.manga-viewer__stage{position:relative;flex:1;min-height:0;display:flex;align-items:center;justify-content:center;overflow:hidden;touch-action:pan-y}
.manga-viewer__unit{display:flex;align-items:center;justify-content:center;gap:2px;height:100%;width:100%;padding:10px}
.manga-viewer__unit img{max-height:100%;max-width:100%;object-fit:contain;background:#000;box-shadow:0 4px 24px rgba(0,0,0,.6)}
.manga-viewer__unit.is-multi{flex-direction:column;justify-content:flex-start;overflow-y:auto;gap:10px}
.manga-viewer__unit.is-multi img{max-height:none;width:100%;max-width:820px;height:auto;border-radius:4px}
.manga-viewer__zone{position:absolute;top:0;bottom:0;width:30%;cursor:pointer;z-index:10}
.manga-viewer__zone--left{left:0}
.manga-viewer__zone--right{right:0}
.manga-viewer__count{position:absolute;bottom:14px;left:50%;transform:translateX(-50%);background:rgba(20,24,36,.85);border:1px solid var(--manga-viewer-border);border-radius:999px;padding:5px 14px;font-size:13px;color:var(--manga-viewer-text);z-index:20;pointer-events:none}
.manga-viewer__scroll{flex:1;min-height:0;overflow-y:auto;display:flex;flex-direction:column;align-items:center;gap:10px;padding:16px}
.manga-viewer__scroll img{width:100%;max-width:820px;height:auto;border-radius:4px;background:#000}
.manga-viewer__page-text{color:var(--manga-viewer-muted);font-size:13px;line-height:1.7;max-width:820px;white-space:pre-wrap;margin:0}")

;; ADR-2607141700: komawari-composed pages (kami.mangaka.komawari-bearing
;; :panel/rect data) render panels absolutely-positioned within one B5-ratio
;; frame instead of a flat list of full-width images.
(def ^:private composed
  ".manga-viewer__composed-page{width:100%;max-width:820px;margin:0 auto}
.manga-viewer__composed-frame{position:relative;width:100%;aspect-ratio:1075/1518;background:#161412;box-shadow:0 4px 24px rgba(0,0,0,.6)}
.manga-viewer__panel{overflow:hidden;border:3px solid #000;background:#e9e3cf;box-sizing:border-box}
.manga-viewer__panel img{width:100%;height:100%;object-fit:cover;display:block}
.manga-viewer__unit .manga-viewer__composed-page{height:100%;max-height:100%;display:flex;align-items:center}
.manga-viewer__unit .manga-viewer__composed-frame{max-height:100%}
.manga-viewer__unit.is-multi .manga-viewer__composed-page{max-width:820px}")

(defn css []
  (str root "\n" grid "\n" reader "\n" composed "\n"))
