;; Preprocessing consists of splitting into sentences, splitting
;; sentences into words, removing words, getting lemmatized words
;; from the wordnet database

(ns summarizer.preprocess
  (:use [summarizer.stopwords]
        [summarizer.wordnet]
        [clojure.contrib.string :only [lower-case substring?]])
  (:import opennlp.tools.sentdetect.SentenceModel
           opennlp.tools.sentdetect.SentenceDetectorME
           opennlp.tools.tokenize.TokenizerModel
           opennlp.tools.tokenize.TokenizerME
           opennlp.tools.postag.POSModel
           opennlp.tools.postag.POSTaggerME
	   java.io.FileInputStream))

;; Trained NLP models
(def base-path "resources/models/")
(def sentence-model (str base-path "en-sent.bin"))
(def token-model (str base-path "en-token.bin"))
(def pos-model (str base-path "en-pos-maxent.bin"))
(def pos-map {"J" "ADJECTIVE" "N" "NOUN" "R" "ADVERB" "V" "VERB"})

(defn get-lemma
  "Returns lemmatization from wordnet. If not
   found, returns token."
  [word]
  (let [token (key word)
        pos (.substring (val word) 0 1)
        pos-jwnl (get pos-map pos)
        synset (get-synset pos-jwnl token)]
    (if synset
       synset
       token)))

(defn remove?
  "True if token is deemed insignificant."
  [word]
  (let [token (lower-case (key word))]
    (or (contains? stop-words token)
        (= (. token length) 1)
        (substring? "'" token))))

(defn reduce-tokens-for-sentence
  "Takes a coll of token/pos pairs. 
   Returns a coll of lemmatized, relevant tokens"
  [sentence]
  (let [filtered (remove remove? sentence)
        reduced (map get-lemma filtered)]
    reduced))

(defn reduce-tokens
  "Takes a coll of colls of token/pos pairs.
   Returns a coll of lemmatized, relavant tokens"
  [sentences]
  (println "Getting lemmatized versions from wordnet...")
  (let [reduced (map reduce-tokens-for-sentence sentences)]
    (map set reduced)))

(defn get-pos-tagger
  []
  (let [modelInputStream (new FileInputStream pos-model)
        model (new POSModel modelInputStream)]
    (new POSTaggerME model)))

(defn POS-tag-for-sentence
  "Takes a coll of tokens. Returns a coll of pos tags.
   Attempts part-of-speech tagging using a trained model."
  [pos-tagger]
  (println "Determining parts of speech...")
  (fn [tokens] (. pos-tagger tag tokens)))

(defn POS-tag
  "Attempts part-of-speech tagging using trained model."
  [sentences pos-tagger]
  (map (POS-tag-for-sentence pos-tagger) sentences))

(defn get-tokenizer
  []
  (let [modelInputStream (new FileInputStream token-model)
        model (new TokenizerModel modelInputStream)]
    (new TokenizerME model)))


(defn token-split-for-sentence
  "Takes a sentence. Returns a coll of tokens.
   Attempts tokenization using a trained model"
  [tokenizer]
  (println "Splitting tokens...")
  (fn [sentence] (. tokenizer tokenize sentence)))

(defn token-split
  "Attempts tokenization using a trained model"
  [sentences tokenizer]
  (map (token-split-for-sentence tokenizer) sentences))

(defn sentence-split
  "Takes a piece of text. Returns a vector of sentences.
   Attempts to split text into sentences using a trained model"
  [text]
  (println "Splitting Sentences...")
  (let [modelInputStream (new FileInputStream sentence-model)
        model (new SentenceModel modelInputStream)
        sentenceDetector (new SentenceDetectorME model)
        sentences (. sentenceDetector sentDetect text)]
    (vec sentences)))

(defn preprocess
  "Takes a coll of sentences. Returns a coll of
   sets of lemmatized, relevant tokens"
  [sentences]
  (let [tokenizer (get-tokenizer)
	tokens (token-split sentences tokenizer)
	pos-tagger (get-pos-tagger)
        pos (POS-tag tokens pos-tagger)
        token-pos (map zipmap tokens pos)
        reduced-tokens (reduce-tokens token-pos)]
    reduced-tokens))
