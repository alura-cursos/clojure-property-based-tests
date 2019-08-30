(ns hospital.logic
  (:require [hospital.model :as h.model]
            [schema.core :as s]))

; código do curso anterior

(defn cabe-na-fila?
  [hospital departamento]
  (some-> hospital
          departamento
          count
          (< 5)))

(defn chega-em
  [hospital departamento pessoa]
  (if (cabe-na-fila? hospital departamento)
    (update hospital departamento conj pessoa)
    (throw (java.lang.IllegalStateException. "Não cabe ninguém neste departamento"))))

(s/defn atende :- h.model/Hospital
        [hospital :- h.model/Hospital, departamento :- s/Keyword]
        (update hospital departamento pop))

(s/defn proxima :- (s/maybe h.model/PacienteID)
        "Retorna o próximo paciente da fila"
        [hospital :- h.model/Hospital, departamento :- s/Keyword]
        (-> hospital
            departamento
            peek))

(defn mesmo-tamanho?
  [hospital, outro-hospital, de, para]
  (= (+ (count (get outro-hospital de)) (count (get outro-hospital para)))
     (+ (count (get hospital de)) (count (get hospital para)))))

(s/defn transfere :- h.model/Hospital
        "Transfere o próximo paciente da fila de para a fila para"
        [hospital :- h.model/Hospital, de :- s/Keyword, para :- s/Keyword]
        {:pre [(contains? hospital de), (contains? hospital para)]
         :post [(mesmo-tamanho? hospital % de para)]}
        (if-let [pessoa (proxima hospital de)]
          (-> hospital
              (atende de)
              (chega-em para pessoa))
          hospital))

(defn total-de-pacientes [hospital]
  (reduce + (map count (vals hospital))))

