(ns metabase-enterprise.serialization.v2.extract
  "Extraction is the first step in serializing a Metabase appdb so it can be eg. written to disk.

  See the detailed descriptions of the (de)serialization processes in [[metabase.models.serialization.base]]."
  (:require [clojure.set :as set]
            [medley.core :as m]
            [metabase-enterprise.serialization.v2.models :as serdes.models]
            [metabase.models.serialization.base :as serdes.base]))

(defn extract-metabase
  "Extracts the appdb into a reducible stream of serializable maps, with `:serdes/meta` keys.

  This is the first step in serialization; see [[metabase-enterprise.serialization.v2.storage]] for actually writing to
  files. Only the models listed in [[serdes.models/exported-models]] get exported.

  Takes an options map which is passed on to [[serdes.base/extract-all]] for each model. The options are documented
  there."
  [opts]
  (let [model-pred (if (:data-model-only opts)
                     #{"Database" "Dimension" "Field" "FieldValues" "Metric" "Segment" "Table"}
                     (constantly true))]
    (eduction cat (for [model serdes.models/exported-models
                        :when (model-pred model)]
                    (serdes.base/extract-all model opts)))))

(defn- descendants-closure [target]
  (loop [to-chase #{target}
         chased   #{}]
    (let [[m i :as item] (first to-chase)
          desc           (serdes.base/serdes-descendants m i)
          chased         (conj chased item)
          to-chase       (set/union (disj to-chase item) (set/difference desc chased))]
      (if (empty? to-chase)
        chased
        (recur to-chase chased)))))

(defn extract-subtree
  "Extracts the targeted entity and all its descendants into a reducible stream of extracted maps.

  The targeted entity is specified as a `[\"SomeModel\" database-id]` pair.

  [[serdes.base/serdes-descendants]] is recursively called on this entity and all its descendants, until the complete
  transitive closure of its descendants are found. This produces a set of `[\"ModelName\" id]` pairs, which entities
  are then extracted the same way as [[extract-metabase]]."
  [{:keys [target] :as opts}]
  (let [closure  (descendants-closure target)
        by-model (->> closure
                     (group-by first)
                     (m/map-vals #(set (map second %))))]
    (eduction cat (for [[model ids] by-model]
                    (eduction (map #(serdes.base/extract-one model opts %))
                              (serdes.base/raw-reducible-query model {:where [:in :id ids]}))))))
