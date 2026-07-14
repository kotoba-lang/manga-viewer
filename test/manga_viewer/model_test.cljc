(ns manga-viewer.model-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [manga-viewer.model :as m]))

(def mangaka-data
  {:title "残響のカルテット"
   :author "GFTD Mangaka AI"
   :pages [{:n 2 :src "/img/zankyo-1-p02"}
           {:n 1 :src "/img/zankyo-1-p01"}]})

(deftest from-mangaka-data-normalizes
  (let [work (m/from-mangaka-data mangaka-data
                                  {:id "zankyo-1"
                                   :url "https://manga.gftd.ai/work/zankyo-1"
                                   :episode "第1話 遺品"
                                   :tags ["音楽"]})]
    (is (= "zankyo-1" (:manga/id work)))
    (is (= [1 2] (map :page/number (:manga/pages work))) "pages sort by number")
    (is (= ["/img/zankyo-1-p01"] (:page/images (first (:manga/pages work)))))
    (is (= "/img/zankyo-1-p01" (:manga/cover work)) "cover defaults to page 1")
    (is (empty? (m/validate work)))))

(def gh-tx
  [{:db/id "did:web:x" :yoro.profile/did "did:web:x"}
   {:db/id "gh.manga/work/demo" :gh.manga/id "demo" :gh.manga/title "Demo"
    :gh.manga/subtitle "Arc 0-1"}
   {:db/id "gh.manga/page/demo/p001" :gh.manga/work "gh.manga/work/demo"
    :gh.manga/pageNumber 1 :gh.manga/title "Opening"
    :gh.manga/postUri "at://did:web:x/app.bsky.feed.post/demo-p001"}
   {:db/id "gh.manga/page/demo/p000" :gh.manga/work "gh.manga/work/demo"
    :gh.manga/pageNumber 0 :gh.manga/title "Cover"
    :gh.manga/postUri "at://did:web:x/app.bsky.feed.post/demo-p000"}
   {:gh.manga/panel-id "a" :gh.manga/page "gh.manga/page/demo/p001"
    :gh.manga/panelNumber 2 :gh.manga/imageUrl "/images/p1-2.png"}
   {:gh.manga/panel-id "b" :gh.manga/page "gh.manga/page/demo/p001"
    :gh.manga/panelNumber 1 :gh.manga/imageUrl "/images/p1-1.png"}
   {:gh.manga/panel-id "c" :gh.manga/page "gh.manga/page/demo/p000"
    :gh.manga/panelNumber 1 :gh.manga/imageUrl "/images/p0-1.png"}
   {:yoro.post/uri "at://did:web:x/app.bsky.feed.post/demo-p001"
    :yoro.post/text "Demo / Opening\nセリフ1\nセリフ2"}])

(deftest from-gh-manga-tx-folds-panels-into-pages
  (let [work (m/from-gh-manga-tx gh-tx {:image-fn #(str/replace % ".png" ".webp")
                                        :author "Ghost Hacker"})]
    (is (= "demo" (:manga/id work)))
    (is (= "Arc 0-1" (:manga/episode work)) "subtitle backs the episode when no arc")
    (is (= [0 1] (map :page/number (:manga/pages work))) "pages sort by number")
    (testing "panels sort by panelNumber and pass through image-fn"
      (is (= ["/images/p1-1.webp" "/images/p1-2.webp"]
             (:page/images (second (:manga/pages work))))))
    (testing "page text comes from the page post, minus the title line"
      (is (= "セリフ1\nセリフ2" (:page/text (second (:manga/pages work)))))
      (is (nil? (:page/text (first (:manga/pages work))))))
    (is (= "/images/p0-1.webp" (:manga/cover work)))
    (is (empty? (m/validate work)))))

(deftest page-count-override-for-index-only-works
  (is (= 45 (m/page-count {:manga/id "x" :manga/title "X" :manga/page-count 45})))
  (is (= 2 (m/page-count {:manga/pages [{:page/number 1 :page/images ["a"]}
                                        {:page/number 2 :page/images ["b"]}]}))))

(deftest from-gh-manga-tx-carries-panel-geometry-when-present
  (let [tx (conj gh-tx
                 {:gh.manga/panel-id "d" :gh.manga/page "gh.manga/page/demo/p000"
                  :gh.manga/panelNumber 2 :gh.manga/imageUrl "/images/p0-2.png"
                  :gh.manga/rect [0.5 0.0 0.5 1.0] :gh.manga/tilt 12.0})
        work (m/from-gh-manga-tx tx)
        p0 (first (:manga/pages work))]
    (testing "only the panel that actually carries :gh.manga/rect appears in :page/panels"
      (is (= 1 (count (:page/panels p0))) "page has 2 panels total, only 1 has :rect")
      (is (= [0.5 0.0 0.5 1.0] (:panel/rect (first (:page/panels p0)))))
      (is (= 12.0 (:panel/tilt (first (:page/panels p0))))))
    (testing "image-fn applies to :panel/imageUrl the same as :page/images"
      (let [work2 (m/from-gh-manga-tx tx {:image-fn #(str % "?w=800")})
            p0-2 (first (:manga/pages work2))]
        (is (= "/images/p0-2.png?w=800" (:panel/imageUrl (first (:page/panels p0-2)))))))
    (testing ":page/images is unaffected by geometry -- both panels still list their image"
      (is (= 2 (count (:page/images p0))))))
  (testing "a page with no geometry-bearing panels gets an empty :page/panels, :page/images unaffected"
    (let [work (m/from-gh-manga-tx gh-tx)
          p1 (second (:manga/pages work))]
      (is (empty? (:page/panels p1)))
      (is (= 2 (count (:page/images p1)))))))

(deftest validate-reports-problems
  (is (= ["missing :manga/id" "missing :manga/title" "no pages"]
         (m/validate {})))
  (is (some #{"page without images"}
            (m/validate {:manga/id "x" :manga/title "X"
                         :manga/pages [{:page/number 1 :page/images []}]})))
  (is (some #{"duplicate page numbers"}
            (m/validate {:manga/id "x" :manga/title "X"
                         :manga/pages [{:page/number 1 :page/images ["a"]}
                                       {:page/number 1 :page/images ["b"]}]}))))
