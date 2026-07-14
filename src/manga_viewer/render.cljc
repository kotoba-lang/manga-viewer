(ns manga-viewer.render
  "Pure-hiccup views (ADR-2607070500). No state, no runtime deps: the
  consumer owns {:mode :unit} and passes handlers — a reagent SPA wires
  on-click fns, an SSR consumer renders the same hiccup to HTML and adds its
  own enhancer. Plain hiccup only (no reagent-specific forms such as :<>).
  Include manga-viewer.style/css once per page."
  (:require [clojure.string :as str]
            [manga-viewer.model :as model]
            [manga-viewer.pagination :as pagination]))

(def mode-labels {:spread "見開き" :single "単頁" :scroll "縦読"})

;; ── index ────────────────────────────────────────────────────────────────────

(defn- work-meta-line [work]
  (let [parts (remove nil? [(:manga/author work)
                            (:manga/episode work)
                            (str (model/page-count work) "ページ")])]
    (str/join " ・ " parts)))

(defn work-card
  "One index card. opts:
    :href-fn   work → href (default :manga/url)
    :target-fn work → link target or nil (e.g. \"_blank\" for external readers)"
  [work {:keys [href-fn target-fn]}]
  (let [href ((or href-fn :manga/url) work)
        target (when target-fn (target-fn work))]
    [:a.manga-viewer__card
     (cond-> {:href href}
       target (assoc :target target :rel "noopener"))
     [:div.manga-viewer__thumb
      (when-let [cover (:manga/cover work)]
        [:img {:src cover :alt (:manga/title work) :loading "lazy"}])]
     [:div.manga-viewer__card-body
      [:h2.manga-viewer__card-title (:manga/title work)]
      [:div.manga-viewer__card-meta (work-meta-line work)]
      (when (seq (:manga/tags work))
        [:div.manga-viewer__tags
         (for [tag (:manga/tags work)]
           ^{:key tag} [:span.manga-viewer__tag tag])])]]))

(defn works-grid [works opts]
  [:div.manga-viewer__grid
   (for [work works]
     (with-meta (work-card work opts) {:key (:manga/id work)}))])

;; ── reader ───────────────────────────────────────────────────────────────────

(defn- page-img [page img loading]
  [:img {:src img
         :alt (or (:page/title page) (str "page " (:page/number page)))
         :loading loading
         :decoding "async"}])

;; ── komawari-composed page (ADR-2607141700) ──────────────────────────────────
;; When a page's panels carry :panel/rect (kami.mangaka.komawari's
;; propose-page-layout output, via manga-viewer.model/from-gh-manga-tx),
;; render them absolutely-positioned within one page frame instead of a flat
;; list of full-width panel images — the frames/gutters/force-line-tilt
;; geometry a "panel-per-image" work would otherwise lose entirely. A page
;; with no :panel/rect-bearing panels never reaches this fn (see stage/
;; scroll-stage below), so every existing panel-per-image work (the common
;; case today) is byte-identical to before.

(defn- panel-style
  "[x y w h] (normalized 0-1) + optional tilt (degrees) → CSS. tilt renders as
  a `skewX` shear on the panel's own box — an approximation of komawari's true
  parallelogram shear (Path2D in the JVM/Java2D renderer, ADR-2607051520), not
  a pixel-exact match, but the same visual language (force-line diagonal
  border) in a context (CSS box layout) that has no native parallelogram-clip
  primitive cheap enough for a browser reader."
  [[x y w h] tilt]
  (cond-> {:position "absolute"
           :left (str (* 100 x) "%") :top (str (* 100 y) "%")
           :width (str (* 100 w) "%") :height (str (* 100 h) "%")}
    tilt (assoc :transform (str "skewX(" (- tilt) "deg)"))))

(defn- composed-panel [panel]
  [:div.manga-viewer__panel {:style (panel-style (:panel/rect panel) (:panel/tilt panel))}
   (when-let [src (:panel/imageUrl panel)]
     [:img {:src src :alt "" :loading "lazy" :decoding "async"}])])

(defn composed-page
  "A geometry-bearing page → one framed page div with each panel
  absolutely-positioned per its :panel/rect (see `panel-style`)."
  [page]
  [:div.manga-viewer__composed-page
   (into [:div.manga-viewer__composed-frame]
         (map composed-panel (:page/panels page)))])

(defn- geometry? [page] (seq (:page/panels page)))

(defn- stage
  "Current unit on the stage. RTL: pages inside a unit render right→left
  (higher page number on the LEFT), and the LEFT tap zone advances."
  [work units unit-idx {:keys [on-prev on-next]}]
  (let [pgs (:manga/pages work)
        current (get units (pagination/clamp-unit units unit-idx))
        multi? (boolean (some #(> (count (:page/images (nth pgs %))) 1) current))]
    [:div.manga-viewer__stage
     (into [:div.manga-viewer__unit {:class (when multi? "is-multi")}]
           (mapcat (fn [pi]
                     (let [page (nth pgs pi)]
                       (if (geometry? page)
                         [(composed-page page)]
                         (map #(page-img page % "eager") (:page/images page)))))
                   (reverse current)))
     [:div.manga-viewer__zone.manga-viewer__zone--left {:on-click on-next}]
     [:div.manga-viewer__zone.manga-viewer__zone--right {:on-click on-prev}]
     [:div.manga-viewer__count
      (pagination/unit-label units unit-idx (count pgs))]]))

(defn- scroll-stage [work]
  (into [:div.manga-viewer__scroll]
        (mapcat (fn [page]
                  (concat (if (geometry? page)
                            [(composed-page page)]
                            (map (fn [img] (page-img page img "lazy")) (:page/images page)))
                          (when (:page/text page)
                            [[:p.manga-viewer__page-text (:page/text page)]])))
                (:manga/pages work))))

(defn reader
  "Reader shell. state: {:mode :spread|:single|:scroll  :unit n}
   handlers: {:on-prev fn :on-next fn :on-mode (fn [mode])
              :back-href str :bar-extra hiccup}"
  [work {:keys [mode unit] :or {mode :single unit 0}}
   {:keys [on-mode back-href bar-extra] :as handlers}]
  (let [units (pagination/units (model/page-count work) mode)]
    [:div.manga-viewer__reader
     [:div.manga-viewer__bar
      (when back-href [:a.manga-viewer__back {:href back-href} "←"])
      [:h1.manga-viewer__title (:manga/title work)]
      (when (:manga/author work)
        [:span.manga-viewer__meta (:manga/author work)])
      [:span.manga-viewer__spacer]
      (for [m (pagination/supported-modes work)]
        ^{:key m}
        [:button.manga-viewer__mode-btn
         {:class (when (= m mode) "is-on")
          :on-click (when on-mode #(on-mode m))}
         (mode-labels m)])
      bar-extra]
     (if (= mode :scroll)
       (scroll-stage work)
       (stage work units unit handlers))]))
