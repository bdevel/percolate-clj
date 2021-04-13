(ns percolate-clj.core
  "Some of this code is based on `clj-luwak` which is more advanced than
  is need here.
  
  https://gitlab.com/tokenmill/clj-luwak/blob/master/src/luwak/phrases.clj

  Uses StandardTokenizer by default (see: http://unicode.org/reports/tr29/)
  Also available, WhitespaceTokenizer which splits on white space only.

  Overview of classes in Luwak:
  https://opensourceconnections.com/blog/2016/02/05/luwak/
  
  JavaDocs
    Lucene: https://lucene.apache.org/core/6_5_0/core/index.html
    Luwak: https://jar-download.com/artifacts/com.github.flaxsearch/luwak/1.5.0/documentation
  "
  (:import (uk.co.flax.luwak Monitor MonitorQuery InputDocument MonitorQueryParser)
           (uk.co.flax.luwak.presearcher MatchAllPresearcher)
           (uk.co.flax.luwak.queryparsers LuceneQueryParser)
           (uk.co.flax.luwak.matchers ScoringMatcher HighlightingMatcher HighlightsMatch$Hit ExplainingMatcher)
           
           (org.apache.lucene.queryparser.complexPhrase ComplexPhraseQueryParser)
           (org.apache.lucene.analysis.standard StandardFilter StandardTokenizer)
           (org.apache.lucene.analysis.core LowerCaseFilter );; WhitespaceTokenizer
           (org.apache.lucene.analysis Analyzer Analyzer$TokenStreamComponents Tokenizer)
           ;; Would be nice to add support for these:
           ;; (org.apache.lucene.document StringField TextField DoublePoint LongPoint IntRange)
           ;; (org.apache.lucene.util BytesRef)
           ;; (org.apache.lucene.analysis.miscellaneous ASCIIFoldingFilter)
           )
  )



;; Documents
;; ================================================================================


(def default-doc-id "doc1");; search a single doc still requires an id
(def default-field "content")

;; To see how StandardTokenizer works, see: http://unicode.org/reports/tr29/
;; An Analyzer builds TokenStreams, which analyze text. It thus represents a
;; policy for extracting index terms from text.  In order to define what
;; analysis is done, subclasses must define their TokenStreamComponents in
;; createComponents(String). The components are then reused in each call to
;; tokenStream(String, Reader).  See docs for
;; org.apache.lucene.analysis.Analyzer to
(defn build-default-text-analyzer
  []
  (let [
        ;;tokenizer (org.apache.lucene.analysis.core.WhitespaceTokenizer.)
        ;;tokenizer (org.apache.lucene.analysis.standard.ClassicTokenizer.)
        tokenizer (StandardTokenizer.)
        ]
    (proxy [Analyzer] []
      (createComponents [^String field-name]
        (Analyzer$TokenStreamComponents.
          tokenizer
          (LowerCaseFilter. tokenizer))))))


(defn ^InputDocument doc-text
  "Makes a InputDocument from text using default-field."
  [^String text]
  (-> (InputDocument/builder default-doc-id)
      (.addField default-field text (build-default-text-analyzer))
      (.build )))

(comment
  (doc-text "this is my free download site")
  (.getDocument (doc-text "this is my free download site")))


;; Monitors
;; ================================================================================

(defn new-monitor
  ""
  []
  (Monitor.
    (proxy [MonitorQueryParser] []
      (parse [queryString metadata]
        (.parse (ComplexPhraseQueryParser.
                  default-field
                  (build-default-text-analyzer)
                  )
                queryString
                #_(ComplexPhraseQueryParser.
                    default-field
                    (build-default-text-analyzer)
                    "fo"))))
    (MatchAllPresearcher.)))


;; Queries
;; ================================================================================

;; query syntax: https://lucene.apache.org/core/6_5_0/queryparsersyntax.html


;; Special + - && || ! ( ) { } [ ] ^ " ~ * ? : \
;; escape with \
;; (1+1):2 use the query: \(1\+1\)\:2
(defn lucene-escape
  ""
  [text]
  ;; escape \ first so you don't escape your escapes
  (let [to-escape (clojure.string/split "\\ && || ! ( ) { } [ ] ^ \" ~ * ? : " #"\s")] 

    (reduce (fn [out c]
              (clojure.string/replace out c (str "\\" c)))
            text
            to-escape)))
(comment
  (println (lucene-escape "Ah! Real monsters?"))
  )


(defn build-query
  "Helper to build query object."
  [id query-string]
  (MonitorQuery. id query-string))

(comment (build-query "q1" "free movies"))

(defn add-query
  "Builds a MonitorQuery from query-string and adds it to monitor.
  Always returns monitor. Will throw Exception if cannot add query."
  [monitor id query-string]
  (try
    (.update monitor ^Iterable (list (MonitorQuery. (str id) query-string)))
    (catch Exception e
      (throw 
        (ex-info (str "Unable to add query to monitor: '" query-string "' " e)
                 {:query     query-string 
                  :exception e}))))
  monitor)

(defn add-queries-from-map
  "Applies add-query to monitor where the query ID is query-map key and the query is the value.
  Returns the monitor."
  [monitor query-map]
  (run! (fn [[k v]]
          (add-query monitor k v))
        query-map)
  monitor)


;; Matchers
;; ================================================================================

(defn matches-from-matcher
  "Given a monitor loaded with queries, some text, and a matcher, will return the results objects."
  [monitor text matcher]
  (let [doc     (doc-text text)
        matches (-> (.match monitor doc matcher)
                    (.getMatches default-doc-id)
                    (.getMatches))]
    matches))


(defn matches-from-scoring-matcher
  [^Monitor monitor text]
  (matches-from-matcher monitor text ScoringMatcher/FACTORY))

(defn matches-from-highlighting-matcher
  [^Monitor monitor text]
  (matches-from-matcher monitor text HighlightingMatcher/FACTORY))

(defn matches-from-explaining-matcher
  ""
  [^Monitor monitor text]
  (matches-from-matcher monitor text ExplainingMatcher/FACTORY))


;; Result formatters
;; ================================================================================

(defn offsets-overlap?
  ""
  [a b]
  (let [[start end] a
        [s e]       b]
    (or ;; does it overlap?
      (and (>= start s)
           (<= start e))
      (and (>= end s)
           (<= end e)))))


(defn move-offset-until-space
  "Moves initial-offset by (f ) until a space is found. Useful for adjusting snippet offsets to capture words."
  [text initial-offset f]
  (let [ex         #"\s" ;; doesn't work with #"\b" which would be ideal.
        max-offset (count text) ]
    (loop [offset initial-offset]
      (if (re-find ex (str (get text offset)))
        offset
        (let [next-offset (f offset)]
          (if (or (< next-offset 0)
                  (> next-offset max-offset))
            offset
            (recur (f offset))))))))

(comment 
  (move-offset-until-space "sammy is-here-today" 2 dec))



(defn snippet-offsets-for-match-offsets
  ""
  [match-offsets text]
  (let [
        ;; Now grab some text before and after the highlight to provide context
        max-len       100
        context-chars 40
        text-len      (count text)

        ;; move offet by context-chars, then continue shift until whitespace
        context-offsets (map (fn [[start end]]
                               (vector (move-offset-until-space text (max 0 (- start context-chars)) dec)
                                       (move-offset-until-space text (min text-len (+ end context-chars)) inc)))
                             match-offsets)
        
        ;; Merge any overlapping highlights
        snippet-offsets (reduce (fn [out offset]
                                  (if (some #(offsets-overlap? offset %) out)
                                    (map (fn [o]
                                           (if (offsets-overlap? offset o)
                                             ;; then merge
                                             (let [s (min (first offset)  (first o))
                                                   e (max (second offset) (second o))]
                                               (if (<= (- e s) max-len)
                                                 (vector s e);; only merge if new snippet is less than max-len
                                                 o))
                                             ;; no overlap, don't merge
                                             o))
                                         out)
                                    (conj out offset)))
                                []
                                context-offsets)
        ]
    snippet-offsets))


(defn snippets-from-offsets
  ""
  [snippet-offsets text]
  (let [snippets (map (fn [[start end]]
                        (clojure.string/trim (subs text start end)))
                      snippet-offsets) ]
    snippets))

(defn snippets-of-re
  ""
  [p text]
  (let [matcher         (.matcher p text)
        match-offsets   (loop [out []]
                          (if (.find matcher)
                            (recur (conj out [(.start matcher) (.end matcher)]))
                            out))
        snippet-offsets (snippet-offsets-for-match-offsets match-offsets text)
        snippets        (snippets-from-offsets snippet-offsets text)]
    snippets))


(declare snippets-from-explaining-highlighter)

(defn query-snippets-re
  "The luwak snippet maker isn't very good as it can't highlight queries with stop words in it.
  This fn extract must parts from a query such as '+(\"foo\" OR \"bar\") baz' and match with a regular expression."
  [query-string text]
  (let [must-quoted-group (map second 
                               (re-seq #"\"([^\"]+)\""
                                       (second (re-find #"\+\(([^\)]+)\)"
                                                        query-string))))
        
        must-quoted (map second
                         (re-seq #"\+(\"[^\"]+\")"
                                 query-string))
        must-words  (map second
                         (re-seq #"\+(\w+)"
                                 query-string))
        to-match    (or must-quoted-group
                        must-quoted
                        must-words)
        ]
    (if to-match
      (take 3 (mapcat #(snippets-of-re (java.util.regex.Pattern/compile % java.util.regex.Pattern/CASE_INSENSITIVE) text)
                      to-match))
      (list ))))

(defn query-snippets
  "Takes query-string and performs a search on text. If matches are found then it returns the a list of snippets."
  [query-string text]
  (let [monitor (new-monitor)
        match   (-> monitor;; will be only one match as only one field, but probably many hits
                    (add-query "q" query-string)
                    (matches-from-highlighting-matcher text)
                    (first))]

    (.close monitor)
    
    (if match
      (if (.error match)
        (query-snippets-re query-string text)
        (let [ hits           (->> match
                                   (.getFields)
                                   (first)
                                   (.getHits match))
              match-offsets   (map (fn [^HighlightsMatch$Hit hit]
                                     (list (.-startOffset hit)
                                           (.-endOffset hit)))
                                   hits)
              snippet-offsets (snippet-offsets-for-match-offsets match-offsets text)
              snippets        (snippets-from-offsets snippet-offsets text)]
          ;; can't take all the matches because a one page put the same title 1000 times and caused problems
          (take 3 snippets)))
      (list );; no matches found
      )))


(defn snippets-from-explaining-highlighter
  "The highlighting matcher errors with disjuction searches using groups of parens.
  This uses the explainer to extract the parts that match the query, then return highlights of those parts
  instead of using the orignal query as highlight targets."
  [query-string text]
  (let [monitor      (-> (new-monitor)
                         (add-query "q" query-string))
        matches      (matches-from-explaining-matcher monitor text)
        explinations (map #(.getExplanation %) matches)
        ex-content   (-> []
                         ;; find content:"hell bob"
                         (into (mapcat #(map second (re-seq #"content\:\"([^\"]+)\"" (str %) )) explinations))
                         ;; content:hello
                         (into (mapcat #(map second (re-seq #"content\:([^\"\s]+)" (str %) )) explinations))
                         ;; query-string:  disjuction
                         (into (mapcat #(map second (re-seq #"query-string\:\s*([^\"\s]+)" (str %) )) explinations))
                         (distinct))

        ;; unfortunately, this doesn't merge nearby snippets
        snippets (mapcat #(query-snippets % text ) ex-content)
        ]
    (.close monitor)
    snippets
    ))


(comment
  ;; this query would error with standard highlighting matcher
  (let [query-string "+(\"Avengers Endgame\") \"Brie Larson\" (\"Karen Gillan\" OR \"Anthony Russo Joe Russo\" ) "
        content      "wactch avengers endgame online and then you see The highlighting matcher has a hard time with disjuction searches using groups of parens.
  This uses the explainer to extract the parts that match the query, then return highlights of those parts
  instead of using the original query as highlight targets actors such as karen gillan and then the best actor is brie larson of denver, ohio."

        s (query-snippets query-string content)
        ]
    s)
  
  (query-snippets "download free" "you and your friends can download here and there for everyone for free here")
  (query-snippets "download free" "find nothing here")
  (query-snippets "+(lazy) (dog OR fox)" "the quick fox jumps over the lazy dog")

  (query-snippets "+(\"Ex On The Beach\") (\"Stephen Bear\" OR \"Jordan Davies\" OR \"Ashley Cain\" OR \"Gaz Beadle\")" "i will watch ex on the beach")
  (query-snippets-re "+(\"Ex On The Beach\") (\"Stephen Bear\" OR \"Jordan Davies\" OR \"Ashley Cain\" OR \"Gaz Beadle\")"
                     "i will watch ex on the beach today")

  )

(defn convert-match
  "Converts a match to a hash-map with snippets and score."
  [monitor match text]
  (let [query-id (.getQueryId match)
        query    (.getQuery (.getQuery monitor (.getQueryId match)))
        score    (.getScore match) ]
    {:score    score
     :query    query
     :query-id query-id
     :snippets (query-snippets query text)}))


(defn match-results
  "Performs the reverse search and returns the results has a hash-map."
  [^Monitor monitor text]
  (let [matches (matches-from-scoring-matcher monitor text)
        items   (map #(convert-match monitor % text) matches)]
    (sort-by #(* -1 (get % :score))
             items)))



;; Easy to use interface
;; ================================================================================

(defn boil
  "Pass in content is a body of text, and a hashmap where the key is the query id and the value is the Lucene query."
  [content query-map]
  (let [monitor (new-monitor)
        results (-> monitor
                    (add-queries-from-map (clojure.walk/stringify-keys query-map))
                    (match-results content)
                    )]
    (.close monitor)
    results))


(defn create-percolator
  "Given a list of items, will call build a query for each item using query-builder-fn,
  will add each query to a new monitor,
  The matching item is assoced to the result as :match"
  [items query-builder-fn]

  (let [item-map  (reduce (fn [out i]
                            (assoc out
                                   (str (gensym "percolate"))
                                   i))
                          {} items)
        query-map (reduce (fn [out [k i]]
                            (assoc out
                                   k
                                   (query-builder-fn i)))
                          {} item-map)
        monitor   (add-queries-from-map (new-monitor) query-map)]
    (with-meta  
      (fn [content]
        (map #(assoc % :match (get item-map (:query-id %)))
             (match-results monitor content)))
      ;; add the monitor to meta so we can close it, which i believe helps with memory usage
      {:monitor monitor})))


(defn close-percolator
  "Calls .close on the monitor for a given searcher."
  [percolator]
  (if-let [m (:monitor (meta percolator)) ]
    (.close m)))



;; Demo
;; ================================================================================


(comment
  (boil "Every good boy does fine on Sunday when the fox jumps over the lazy dog."
        {:days    "sunday OR monday OR tuesday"
         :animals "cat OR dog OR fox"
         :places  "seattle OR portland OR chicago"
         })

  (boil "Every good boy does fine on Sunday when the fox jumps over the lazy dog."
        {;;:must "+(box OR foxx) dog"
         ;;:filter-non-scoring "+(dog OR fox)^0 boy"
         :fuzzy "+(dogg OR box~)"
         ;;:negative "-(cat whale dog) boy"
         })

  ;; Testing stopwords not being applied:
  (map :query (boil "I will watch and see for the people in their cars."
                    {:a      "+\"and\" the +people +of"
                     :quoted "\"For the people\""
                     :not    "\"and the people\""
                     :basic  "people"
                     }))


  ;; test that punction is being ignored
  (map :query-id (boil "I will watch Movie: The, Sequal tonight with some people."
                       {:nopunc "movie the sequal"
                        :x      "movie the sequal"
                        }))

  ;; exlamations are weird.
  ;; Must escape them, use (lucene-escape text) even
  (map :query-id (boil "I will watch Movie: The, Sequal! tonight with some people."
                       {:exlam  "Movie The Sequal\\!"
                        :exlam2 "\"Movie: The, Sequal\\!\""
                        :nopunc "movie the sequal"
                        :s      "sequal"
                        :se     "sequal!"
                        :sec    "Sequal!"
                        :seq    "\"sequal!\""
                        :x      "movie the sequal"
                        :x2     "movie the"
                        }))

  ;; hashtags, @ and hyphens?
  (map :query-id (boil "I will #watch #tv tonight# @bob again take-tom-home from here."
                       {:wh    "#watch"
                        :w     "watch"
                        :tn    "tonight@"
                        :b     "bob"
                        :ba    "@bob"
                        :tom   "tom"
                        :ttom  "take-tom"
                        :ttomh "take-tom-home"
                        
                        }))

  (map :query-id (boil
                   "Description: https://www.youtube.com/watch v xx v значит вендетта xdf"
                   ;;"Description: https://www.youtube.com/watch v xx v значит вендетта xdf v字仇杀队 watch"
                   {:v1 "+( \"v字仇杀队 watch\"~8 OR \"watch v字仇杀队\"~8)"
                    :k1 "+( \"v브이 포 벤데타 watch\"~8 OR \"watch v브이 포 벤데타\"~8)"
                    :ru "+(\"v значит вендетта\")"
                    }))


  ;; Searcher demo
  (let [items    [{:name "dogs" :query "dog OR k9 OR puppy OR doggy"}
                  {:name "cats" :query "cat OR kitty OR kitten"}]
        searcher (create-percolator items :query) ]
    (searcher "Every good boy does fine on Sunday when the fox jumps over the lazy dog."))



  ;; useful for inspecting java objects
  #_(defn all-methods [x]
    (->> x clojure.reflect/reflect 
         :members 
         (filter :return-type)  
         (map :name) 
         sort 
         (map #(str "." %) )
         distinct
         ;;clojure.pprint/pprint
         (run! println)
         ))
  ;; (all-methods "")
  )

