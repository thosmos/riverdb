(ns riverdb.roles)

(defn has-role? [user role-key]
  (if
    (seq
      (filter (fn [role]
                (= (:role/type role) role-key)) (:user/roles user)))
    true
    false))

(defn admin?
  ([roles]
   (admin? #{:role.type/riverdb-admin :role.type/admin} roles))
  ([role-ks roles]
   (when (and (seq role-ks) (seq roles))
     (reduce
       (fn [ok role]
         (if ((set role-ks) (:role/type role))
           true
           ok))
       false roles))))



(defn roles->agencies [roles]
  (when (seq roles)
    (reduce
      (fn [result role]
        (if-let [agency (:role/agency role)]
          (conj result (:agencylookup/AgencyCode agency))
          (if (= (:role/type role) :role.type/riverdb-admin)
            (conj result "ALL")
            result)))
      [] roles)))

(defn roles->agencies2 [roles]
  (when (seq roles)
    (reduce
      (fn [result role]
        (when-let [agency (:role/agency role)]
          (conj result agency)))
      [] roles)))

(defn user->roles [user]
  (map #(select-keys % [:role/type :role/agency]) (:user/roles user)))