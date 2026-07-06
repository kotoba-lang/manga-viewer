(ns manga-viewer.model
  "Normalized manga work model + adapters (ADR-2607070500).

  A work is a plain EDN map:

    {:manga/id      \"zankyo-1\"
     :manga/title   \"残響のカルテット\"
     :manga/author  \"GFTD Mangaka AI\"
     :manga/episode \"第1話 遺品\"          ; optional
     :manga/tags    [\"音楽\" \"超常ドラマ\"] ; optional
     :manga/cover   \"https://…/p01\"        ; optional (index thumbnail)
     :manga/url     \"https://…/work/…\"     ; optional (canonical reader URL)
     :manga/pages   [{:page/number 1
                      :page/title  \"Opening\"   ; optional
                      :page/text   \"…\"          ; optional (caption/dialogue)
                      :page/images [\"https://…\"]}]}

  A page has one image (composed page renders, e.g. the manga.gftd.ai reader)
  or several (panel-per-image works, e.g. the aozora Ghost Hacker projection).
  Pagination/mode support derives from that distinction — see
  manga-viewer.pagination/supported-modes."
  (:require [clojure.string :as str]))

(defn page-count [work]
  (count (:manga/pages work)))

(defn image-count [work]
  (reduce + 0 (map #(count (:page/images %)) (:manga/pages work))))

(defn validate
  "Vector of human-readable problems; empty when the work is well-formed."
  [work]
  (let [pages (:manga/pages work)
        numbers (map :page/number pages)]
    (cond-> []
      (str/blank? (str (:manga/id work)))
      (conj "missing :manga/id")

      (str/blank? (str (:manga/title work)))
      (conj "missing :manga/title")

      (empty? pages)
      (conj "no pages")

      (some #(empty? (:page/images %)) pages)
      (conj "page without images")

      (not= (count numbers) (count (distinct numbers)))
      (conj "duplicate page numbers"))))

;; ── adapters ─────────────────────────────────────────────────────────────────

(defn from-mangaka-data
  "manga.gftd.ai reader `DATA` shape → work:
  {:title … :author … :pages [{:n 1 :src \"/img/…\"}]}. Extra work metadata
  (id/url/cover/episode/tags) comes from opts — the DATA blob doesn't carry it."
  ([data] (from-mangaka-data data nil))
  ([{:keys [title author pages]} {:keys [id url cover episode tags]}]
   (let [sorted (sort-by :n pages)]
     {:manga/id (or id title)
      :manga/title title
      :manga/author author
      :manga/episode episode
      :manga/tags (vec (or tags []))
      :manga/cover (or cover (:src (first sorted)))
      :manga/url url
      :manga/pages (mapv (fn [{:keys [n src]}]
                           {:page/number n :page/images [src]})
                         sorted)})))

(defn- page-text
  "Body of a page's :yoro.post/text — the aozora projection puts
  `<work title> / <page title>` on the first line, dialogue after."
  [text]
  (when text
    (let [body (->> (str/split-lines text) (remove str/blank?) (drop 1))]
      (when (seq body) (str/join "\n" body)))))

(defn from-gh-manga-tx
  "aozora `:gh.manga/*` projection tx (seq of entity maps, the shape
  aozora.appview.manga/work->tx emits) → work. Panels fold into their page's
  :page/images sorted by :gh.manga/panelNumber; the page's :yoro.post/* entity
  supplies :page/text. opts:
    :image-fn  url → url (e.g. .png → .webp display swap), default identity
    :author :url :cover :tags — work metadata the tx doesn't carry"
  ([tx] (from-gh-manga-tx tx nil))
  ([tx {:keys [image-fn author url cover tags]}]
   (let [image-fn (or image-fn identity)
         work (first (filter :gh.manga/title tx))
         work-eid (:db/id work)
         pages (->> tx
                    (filter #(= work-eid (:gh.manga/work %)))
                    (sort-by #(or (:gh.manga/pageNumber %) 0)))
         panels-by-page (group-by :gh.manga/page (filter :gh.manga/panel-id tx))
         text-by-uri (into {} (keep (fn [e]
                                      (when-let [uri (:yoro.post/uri e)]
                                        [uri (:yoro.post/text e)]))
                                    tx))
         page-images (fn [page]
                       (->> (get panels-by-page (:db/id page))
                            (sort-by #(or (:gh.manga/panelNumber %) 0))
                            (keep :gh.manga/imageUrl)
                            (mapv image-fn)))
         first-image (some seq (map page-images pages))]
     (when work
       {:manga/id (:gh.manga/id work)
        :manga/title (:gh.manga/title work)
        :manga/author author
        :manga/episode (or (:gh.manga/arc work) (:gh.manga/subtitle work))
        :manga/tags (vec (or tags []))
        :manga/cover (or cover (first first-image))
        :manga/url url
        :manga/pages (mapv (fn [page]
                             (cond-> {:page/number (or (:gh.manga/pageNumber page) 0)
                                      :page/images (page-images page)}
                               (:gh.manga/title page)
                               (assoc :page/title (:gh.manga/title page))

                               (page-text (get text-by-uri (:gh.manga/postUri page)))
                               (assoc :page/text (page-text (get text-by-uri (:gh.manga/postUri page))))))
                           pages)}))))
