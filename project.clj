(defproject percolate-clj "0.1.0"
  :description "Reverse search engine using Lucene query syntax."
  :url "https://https://github.com/bdevel/percolate-clj"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.github.flaxsearch/luwak "1.5.0"]
                 ]
  :repl-options {:init-ns percolate-clj.core})
