(ns theta.ui.comp)

#_(div :.item
    (span {:key "site" :style {}}
      "Site: "
      (select {:style    {:width "150px"}
               :value    ""
               :onChange #(let [st (.. % -target -value)
                                st (if (= st "")
                                     nil
                                     st)]
                            (debug "set site" st))}
        ;(fm/set-value! this :ui/site st))}
        (into
          [(option {:value "" :key "none"} "")] []
          #_(doall
              (for [{:keys [db/id stationlookup/StationName stationlookup/StationID]} sites]
                (option {:value id :key id} (str StationID ": " StationName))))))))