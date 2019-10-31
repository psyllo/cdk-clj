(ns stedi.jsii.import
  (:require [clojure.spec.test.alpha :as stest]
            [stedi.jsii.fqn :as fqn]
            [stedi.jsii.impl :as impl]
            [stedi.jsii.spec :as spec]))

(defn- intern-initializer
  [impl-ns-sym alias-sym c]
  (let [fqs
        (symbol
          (intern impl-ns-sym '-initializer
                  (fn [& args]
                    (impl/create c args))))]
    (spec/load-spec fqs)
    (stest/instrument fqs)
    (intern impl-ns-sym alias-sym c)
    (spec/load-spec (symbol (name impl-ns-sym) (name alias-sym)))))

(defn- intern-methods
  [target-ns-sym methods c]
  (doseq [{:keys [static] :as method} methods]
    (let [method-sym (-> method (:name) (symbol))
          fqs
          (symbol
            (intern target-ns-sym method-sym
                    (fn [& args]
                      (impl/-invoke
                        (if static c (first args))
                        {:op   method-sym
                         :args (if static args (rest args))}))))]
      (spec/load-spec fqs)
      (stest/instrument fqs))))

(defn- intern-enum-members
  [target-ns-sym members c]
  (doseq [member members]
    (let [member-sym  (-> member (:name) (symbol))
          member-k    (keyword member-sym)
          enum-member (get c member-k)]
      (intern target-ns-sym member-sym enum-member))))

(defn import-fqn
  [fqn alias-sym]
  (let [target-ns-sym (fqn/fqn->ns-sym fqn)
        impl-ns-sym   (symbol (str target-ns-sym ".impl"))]
    (when-not (find-ns target-ns-sym)
      (let [c (impl/get-class fqn)

            {:keys [methods members]} (impl/get-type-info c)]
        (create-ns target-ns-sym)
        (create-ns impl-ns-sym)
        (ns-unmap impl-ns-sym alias-sym)
        (intern-initializer impl-ns-sym alias-sym c)
        (intern-methods target-ns-sym methods c)
        (intern-enum-members target-ns-sym members c)))
    (ns-unmap *ns* alias-sym)
    (alias alias-sym target-ns-sym)
    (refer impl-ns-sym :only [alias-sym])))

(comment
  (import-fqn "@aws-cdk/core.App" 'App)
  (import-fqn "@aws-cdk/core.Stack" 'Stack)
  (import-fqn "@aws-cdk/aws-lambda.Function" 'Function)
  (import-fqn "@aws-cdk/aws-lambda.Runtime" 'Runtime)
  (import-fqn "@aws-cdk/aws-lambda.Tracing" 'Tracing)
  (import-fqn "@aws-cdk/aws-lambda.Code" 'Code)
  
  (let [stack (Stack)]
    (Function stack
              "hello"
              {:code    (Code/fromAsset "src")
               :handler "hello"
               :runtime (:JAVA_8 Runtime)}))
  )
