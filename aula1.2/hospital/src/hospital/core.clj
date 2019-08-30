(ns hospital.core
  (:require [clojure.test.check.generators :as gen]))

; usando virgula somente para deixar claro a QUANTIDADE DE SAMPLES
(println (gen/sample gen/boolean, 100))
(println (gen/sample gen/int, 100))
(println (gen/sample gen/string))
(println (gen/sample gen/string-alphanumeric, 100))

; não usei virgula de proposito tambem para indicar os parametros do vetor
; so pra ficar claro educacionalmente, na pratica, arrancaria as virgulas
(println (gen/sample (gen/vector gen/int 15), 100))
(println (gen/sample (gen/vector gen/int 1 5), 100))
(println (gen/sample (gen/vector gen/int), 100))
 