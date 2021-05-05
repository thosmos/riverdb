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
   (if-not (set? roles)
     (admin? role-ks #{roles})
     (when
       (and (set? role-ks) (set? roles))
       (reduce
         (fn [ok role]
           (if ((set role-ks) (:role/type role))
             true
             ok))
         false roles)))))

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

(defn user->roles [user]
  (map #(select-keys % [:role/type :role/agency]) (:user/roles user)))

(defn user->role [user]
  (:user/role user))
