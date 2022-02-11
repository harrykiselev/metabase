(ns metabase.models.timeline
  (:require [metabase.models.interface :as i]
            [metabase.models.permissions :as perms]
            [metabase.util :as u]
            [toucan.db :as db]
            [toucan.hydrate :refer [hydrate]]
            [toucan.models :as models]))

(models/defmodel Timeline :timeline)

;;;; functions

(defn timelines-for-collection
  "Load timelines based on `collection-id` passed in (nil means the root collection). Hydrates the events on each
  timeline at `:events` on the timeline."
  [collection-id {:keys [include archived]}]
  (let [filter-fn (fn [e] (filter #(= (:archived %) archived) e))]
    (if include
      (as-> (db/select Timeline :collection_id collection-id) <>
          (hydrate <> :creator [:events :creator])
          (map #(update % :events filter-fn) <>))
      (hydrate (db/select Timeline :collection_id collection-id) :creator))))

(u/strict-extend (class Timeline)
  models/IModel
  (merge
   models/IModelDefaults
   {:properties (constantly {:timestamped? true})})

  i/IObjectPermissions
  perms/IObjectPermissionsForParentCollection)