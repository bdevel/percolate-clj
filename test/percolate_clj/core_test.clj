(ns percolate-clj.core-test
  (:require [clojure.test :refer :all]
            [percolate-clj.core :refer :all]))

(deftest core-test
  (testing "searching"

    (let [items    [{:name "dogs" :query "dog OR k9 OR puppy OR doggy"}
                    {:name "cats" :query "cat OR kitty OR kitten"}]
          searcher (create-percolator items :query)
          res (searcher "Every good boy does fine on Sunday when the fox jumps over the lazy dog.")]
      (is (= 1 (count res)))
      (is (= (-> items first :query)
             (-> res first :query)))
      (is (= (-> items first)
             (-> res first :match)))
      )))
