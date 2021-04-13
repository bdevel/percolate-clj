# percolate-clj

A Clojure library used to filter incoming documents against a large list of
full-text search queries. Also called, 'reverse search' or 'document routing',
this library can be used to monitor high volumes of news or social media posts using
complex boolean expressions, fuzzy matching, and proximity searches.

Under the hood, it is uses the [Luwak](https://github.com/flaxsearch/luwak)
and [Apache Lucene](https://lucene.apache.org/). See [Lucene Query
Syntax](https://lucene.apache.org/core/6_5_0/queryparsersyntax.html) for more details.

## Usage

For Leiningen/Boot use `[percolate-clj "0.1.0"]` for other
see [percolate-clj on clojars.org](https://clojars.org/percolate-clj).


```clojure

(:require [clojure.test :refer :all]
            [percolate-clj.core :refer [boil create-percolator close-percolator]])
            
;; Easiest way to play with the percolator is to use the boil method
;; but this builds a monitor each time so not for performance.
(boil "Every good boy does fine on Sunday when the fox jumps over the lazy dog."
        {:days    "sunday OR monday OR tuesday"
         :animals "cat OR dog OR fox"
         :places  "seattle OR portland OR chicago"
         })

;; It's better to create-percolator and store it in an atom or promise
;; if you plan to update the searcher occasionally.

(def things-to-find
  [{:name "dogs" :query "dog OR k9 OR puppy OR doggy"}
   {:name "cats" :query "cat OR kitty OR kitten"}])

(def searcher
  (create-percolator things-to-find :query))

(searcher "Every good boy does fine on Sunday when the fox jumps over the lazy dog.")
;; Returns:
;; ({:score    0.28004453,
;;   :query    "dog OR k9 OR puppy OR doggy",
;;   :query-id "percolate8854",
;;   :snippets ("Sunday when the fox jumps over the lazy dog."),
;;   :match    {:name "dogs", :query "dog OR k9 OR puppy OR doggy"}})


;; If you need to re-create the percolator, it's a good idea to close the old one.
(close-percolator searcher)

```

The default tokenizer and matcher does not seem to work with Chinese queries and
documents. Most other languages should work.


## License

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
