(ns riverdb.graphql.mutations
  (:require [clojure.tools.logging :as log :refer [debug info warn error]]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [buddy.hashers :as hashers]
            [buddy.core.hash :as hash]
            [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [thosmos.util :as tu]
            [datomic.api :as d]
            [clojure.walk :as walk]

            [riverdb.state :as st :refer [db state cx uri uri-dbf]]
            [riverdb.auth :as auth]
            [riverdb.roles :as roles]
            [riverdb.model.user :as user]))


(defn resolve-changeit [context {:keys [token maybe yes] :as args} value]
  (debug "CHANGEIT!" args value)
  (debug "MUTATION USER: " (:user context))
  "hmm")

(defn resolve-change-user-name [context {:keys [name] :as args} _]
  (debug "CHANGE USER NAME!" args (:user context))
  (let [result (user/set-name (:user context) name)]
    (debug "CHANGE RESULT: " result)
    (tu/walk-remove-ns result)))

(defn resolve-set-password [context {:keys [password] :as args} _]
  (debug "CHANGE PASSWORD!" args (:user context))
  (let [result (user/set-password (:user context) (auth/hash-password password))]
    (debug "CHANGE RESULT: " result)
    (tu/walk-remove-ns result)))

(defn resolve-auth [context args _]
  (debug "AUTH!!!!" args)

  (if (nil? args)
    (let [user? (:user context)]
      (debug "NIL AUTH")
      (if user?
        {:user  (-> user?
                  user/return-user
                  tu/walk-remove-ns)
         :token (:auth-token context)}
        {}))
    (try
      (let [email     (:email args)
            password  (:password args)
            user      (when email
                        (user/pull-email->user (:email args)))
            verified? (:user/verified? user)]
        (cond
          (not verified?)
          {:success false
           :msg     "unverified user"}
          user
          (-> (auth/auth-password (assoc args :verify (:user/password user)))
            (assoc :user (user/return-user user))
            tu/walk-remove-ns)
          :else
          {:success false
           :msg     "auth failed"}))
      (catch Exception ex (do
                            (debug "Auth failed: ", (.getMessage ex))
                            {:msg "invalid auth" :success false})))))

(defn resolve-unauth [context args _]
  (debug "RESOLVE UNAUTH!!")
  (try
    (auth/unauth)
    (catch Exception ex
      (do
        (debug "Unauth failed: " (.getMessage ex))))))

(defn resolve-reset-password [context {:keys [email] :as args} _]
  (debug "RESET PASSWORD!" args)
  (if email
    (do
      ;; TODO save verify to user record
      (debug "send verification email here"))
    (resolve-as "" {:msg "invalid email"})))


(defn convert-refs [ent]
  ;(debug "INPUT ENTITY" ent)
  (let [out (reduce-kv
              (fn [ent k v]
                (if (and (map? v) (:id v))
                  (let [v-id (:id v)
                        v-id (if (= (type v-id) java.lang.String)
                               (Long/parseLong v-id)
                               v-id)]
                    (assoc ent k v-id))
                  ent))
              ent ent)]
    ;(debug "OUTPUT ENTITY" out)
    out))


(defn resolve-entity-update [ent]
  (fn [ctx args value]
    (let [user?      (:user ctx)
          roles?     (:user/roles user?)
          is-admin?  (roles/has-role? user? :role.type/riverdb-admin)
          _          (debug "IS Admin?" is-admin? user?)
          selection  (:com.walmartlabs.lacinia/selection ctx)
          selections (vec (:selections selection))
          fields     (vec (map :field selections))
          ;table      (get selection :field)
          ;table-nm   (name table)
          table-nm   ent
          id?        (:id args)
          ent?       (when id?
                       (d/pull (db) '[:db/id] (Long/parseLong id?)))
          args       (dissoc args :id)
          m-args     (into {} (map (fn [[k v]] [(keyword table-nm (name k)) v]) args))
          m-args     (convert-refs m-args)
          _          (debug "ARGS:" args ", ID? " id? ", Ent? " ent? ", m-args " m-args)
          entity     (when ent?
                       (merge ent? m-args))]
      (debug "ENTITY UPDATE!!" ent m-args fields entity)

      (cond
        (not is-admin?)
        (do
          (debug "UPDATE FAILED: not an admin")
          {:success false
           :msg     "Must be a RiverDB admin"})

        entity
        (try
          (let [tx @(d/transact (cx) [entity])]
            (debug "TX result" tx)
            entity)

          (catch Exception ex
            (do
              (debug "UPDATE FAILED" (.getMessage ex))
              {:success false
               :msg     (.getMessage ex)})))
        :default
        (do
          (debug "UPDATE FAILED: default handler")
          {:success false
           :msg     "Unknown error"})))))


(defn walk-convert-dbids
  "ex: (walk-add-ns-to-key coll)
  will change all instances of :id to :db/id at any depth and will parse string ids to long"
  [m]
  (let [result (walk/postwalk
                 (fn [form]
                   (if
                     (map? form)
                     (into {}
                       (for [[k v] form]
                         (if (= k :id)
                           [:db/id (if (string? v) (Long/parseLong v) v)]
                           [k v])))
                     form))
                 m)]
    ;(debug "convert DBIDs result" m result)
    result))



(defn add-ns-to-keys [ns m]
  (let [result (into {}
                 (for [[k v] m]
                   (if (keyword? k)
                     [(keyword ns (name k)) v]
                     [k v])))]
    ;(debug "ADD NS Result" result)
    result))

(defn resolve-entity-create [ent]
  (fn [ctx args value]
    (debug "ENTITY CREATE" ent args)
    (let [selection  (:com.walmartlabs.lacinia/selection ctx)
          selections (vec (:selections selection))
          fields     (map :field selections)
          ;tk             (get selection :field)
          id-field?  (some #{:id} fields)
          fields     (if id-field?
                       (remove #(= % :id) fields)
                       fields)

          fields     (vec (->> fields
                            (map name)
                            (remove #(or (str/starts-with? % "__")))))

          table      ent
          user?      (:user ctx)
          roles?     (:user/roles user?)
          is-admin?  (roles/has-role? user? :role.type/riverdb-admin)

          entity     (when args
                       (->> args
                         (walk-convert-dbids)
                         (add-ns-to-keys ent)))]

      (debug "CREATE ENTITY" table (pr-str entity) fields)
      ;(throw (Exception. "Must be a RiverDB admin"))

      (let [tx       @(d/transact (cx) [entity])
            _        (debug "TX" tx)
            first-id (val (first (:tempids tx)))
            entity   (when first-id
                       (-> entity
                         (assoc :db/id first-id)
                         (tu/walk-remove-ns)))]

        (debug "First ID" first-id "TX result" tx)
        entity))))


(defn resolve-entity-delete [ent]
  (fn [ctx args value]
    (debug "ENTITY DELETE" args)))


