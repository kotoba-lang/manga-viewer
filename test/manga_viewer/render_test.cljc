(ns manga-viewer.render-test
  (:require [clojure.test :refer [deftest is testing]]
            [manga-viewer.render :as r]))

(defn- flatten-hiccup [form]
  (tree-seq #(or (vector? %) (seq? %)) seq form))

(defn- imgs [form]
  (->> (flatten-hiccup form)
       (filter #(and (vector? %) (= :img (first %))))
       (map #(:src (second %)))))

(defn- contains-str? [form s]
  (boolean (some #(= s %) (flatten-hiccup form))))

(def spread-work
  {:manga/id "w" :manga/title "作品" :manga/author "作者"
   :manga/pages (mapv (fn [n] {:page/number n :page/images [(str "p" n)]})
                      (range 1 8))})

(def panel-work
  {:manga/id "gh" :manga/title "Ghost Hacker"
   :manga/pages [{:page/number 0 :page/images ["c1" "c2"] :page/text "セリフ"}
                 {:page/number 1 :page/images ["d1"]}]})

(deftest works-grid-renders-cards
  (let [grid (r/works-grid [{:manga/id "a" :manga/title "A" :manga/author "X"
                             :manga/cover "cov-a" :manga/url "https://ext/a"
                             :manga/tags ["tag1"]
                             :manga/pages [{:page/number 1 :page/images ["i"]}]}]
                           {:target-fn (constantly "_blank")})]
    (is (contains-str? grid "A"))
    (is (contains-str? grid "tag1"))
    (is (= ["cov-a"] (imgs grid)))
    (testing "href/target flow through"
      (let [card-attrs (->> (flatten-hiccup grid)
                            (filter #(and (vector? %) (= :a.manga-viewer__card (first %))))
                            first second)]
        (is (= "https://ext/a" (:href card-attrs)))
        (is (= "_blank" (:target card-attrs)))))))

(deftest reader-spread-renders-rtl
  (let [view (r/reader spread-work {:mode :spread :unit 1} {})]
    (testing "unit [1 2] renders higher page number first (left)"
      (is (= ["p3" "p2"] (imgs view))))
    (is (contains-str? view "2-3 / 7"))
    (is (contains-str? view "見開き"))))

(deftest reader-single-shows-one-page
  (let [view (r/reader spread-work {:mode :single :unit 3} {})]
    (is (= ["p4"] (imgs view)))
    (is (contains-str? view "4 / 7"))))

(deftest reader-scroll-shows-everything
  (let [view (r/reader panel-work {:mode :scroll} {})]
    (is (= ["c1" "c2" "d1"] (imgs view)))
    (is (contains-str? view "セリフ"))))

(deftest reader-hides-spread-for-panel-works
  (let [view (r/reader panel-work {:mode :single :unit 0} {})]
    (is (not (contains-str? view "見開き"))
        "panel-per-image works only offer 単頁/縦読")
    (is (= ["c1" "c2"] (imgs view)) "all panel images of the current page stack")))
