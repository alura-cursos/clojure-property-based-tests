(ns hospital.logic-test
  (:use clojure.pprint)
  (:require [clojure.test :refer :all]
            [hospital.logic :refer :all]
            [hospital.model :as h.model]
            [clojure.test.check.clojure-test :refer (defspec)]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [schema.core :as s]
            [schema-generators.generators :as g]))

(s/set-fn-validation! true)

; são testes ESCRITOS baseados em exemplos
(deftest cabe-na-fila?-test

  (testing "Que cabe numa fila vazia"
    (is (cabe-na-fila? {:espera []}, :espera)))

  ; o doseq com um símbolo e uma sequência gerada funciona
  ; mas.... talvez não seja o que queremos em example-based manual, vamos
  ; ver um problema de 2 símbolos
  (testing "Que cabe pessoas em filas de tamanho até 4 inclusive"
    (doseq [fila (gen/sample (gen/vector gen/string-alphanumeric 0 4) 100)]
      (is (cabe-na-fila? {:espera fila}, :espera))))

  (testing "Que não cabe na fila quando a fila está cheia"
    (is (not (cabe-na-fila? {:espera [1 5 37 54 21]}, :espera))))

  ; one off da borda do limite pra cima
  (testing "Que não cabe na fila quando tem mais do que uma fila cheia"
    (is (not (cabe-na-fila? {:espera [1 2 3 4 5 6]}, :espera))))

  ; dentro das bordas
  (testing "Que cabe na fila quando tem gente mas não está cheia"
    (is (cabe-na-fila? {:espera [1 2 3 4]}, :espera))
    (is (cabe-na-fila? {:espera [1 2]}, :espera)))

  (testing "Que não cabe quando o departamento não existe"
    (is (not (cabe-na-fila? {:espera [1 2 3 4]}, :raio-x)))))


; aqui tivemos um problema
; o doseq na unha gera uma multiplicação de casos
; incluindo muuuuuitos casos repetidos
; que não tem nada ligado com o que queremos
;(deftest chega-em-test
;  (testing "Que é colocada uma pessoa em filas menores que 5"
;    (doseq [fila (gen/sample (gen/vector gen/string-alphanumeric 0 4) 10)
;            pessoa (gen/sample gen/string-alphanumeric 5)]
;      (println pessoa fila)
;      (is (= 1 1)) ; só para mostrar que são 50 asserts (10 * 5)
;      )
;    ))

; muito importante lembrar que se você está rodando um repl continuo e recarregando
; os testes. você corre o risco de uma função que foi definida antigamente continuar
; carregada no seu namespace e continuar rodando ela. nesse caso lembrar de restart do repl

; o teste a seguir é generativo e funciona
; mas..... o resultado dele parece MUITO uma cópia do nosso código implementado
; {:espera (conj fila pessoa)} == o código que eu escrevi lá
; se eu coloquei um bug lá, provavelmente eu coloquei o bug aqui, e ai vai dar true, e o bug continua
; não importa se escrevi o teste ANTES ou DEPOIS.... é o mesmo código. (não é 100%)
; mas a medida que ficasse mais complexo o teste
(defspec coloca-uma-pessoa-em-filas-menores-que-5 100
         (prop/for-all
           [fila (gen/vector gen/string-alphanumeric 0 4)
            pessoa gen/string-alphanumeric]
           (is (= {:espera (conj fila pessoa)}
                  (chega-em {:espera fila} :espera pessoa)))))

; coloquei sufixo, mas voce vai ver prefixo também no mundo
; não sou o maior fã, mas é o que a vida nos oferece
(def nome-aleatorio-gen
  (gen/fmap clojure.string/join
            (gen/vector gen/char-alphanumeric 5 10)))

(defn transforma-vetor-em-fila [vetor]
  (reduce conj h.model/fila-vazia vetor))

(def fila-nao-cheia-gen
  (gen/fmap
    transforma-vetor-em-fila
    (gen/vector nome-aleatorio-gen 0 4)))

; abordagem razoavel porem horrivel, uma vez que usamos o tipo e o tipo do tipo
; para fazer um cond e pegar a exception que queremos
; uma alternativa seria usar bibliotecas como a catch-data
; LOG AND RETHROW é ruim. pq? pq se voce pegou, eh pq vc queria tratar
; pq vc pegou se vc SABIA que nao ia tratar?
; a resposta? pq a linguagem nos forcou a jogar ex-info. nao eh que nos forcou
; mas todas as pessoas usam ex-info, entao nos forcou...
;(defn transfere-ignorando-erro [hospital para]
;  (try
;    (transfere hospital :espera para)
;    (catch clojure.lang.ExceptionInfo e
;      (cond
;        (= :fila-cheia (:type (ex-data e))) hospital
;        :else (throw e)))))

; abordagem mais interessante pois evita log and rethrow
; mas perde o "poder" de ex-info (ExceptionInfo)
; e ainda tem o problema de que outras partes do meu codigo ou
; do codigo de outras pessoas pode jogar IllegalStateException
; e eu estou confundindo isso com fila cheia
; para resolver isso, so criando minha propria exception
; mas ai caio no boom de exceptions no sistema (tenho q criar varios tipos)
; OU criar variacoes de tipos como fizemos no ex-info
; eu, guilherme, sou fã de criar muitos tipos de exceptions
; mas entendo que a comunidade não é fã.
; tem tambem todos os outros caminhos que discutimos no curso onde falamos
; sobre tratamento de erro
(defn transfere-ignorando-erro [hospital para]
  (try
    (transfere hospital :espera para)
    (catch IllegalStateException e
      hospital)))

(defspec transfere-tem-que-manter-a-quantidade-de-pessoas 50
         (prop/for-all
           [
            espera (gen/fmap transforma-vetor-em-fila (gen/vector nome-aleatorio-gen 0 50))
            raio-x fila-nao-cheia-gen
            ultrasom fila-nao-cheia-gen
            vai-para (gen/vector (gen/elements [:raio-x :ultrasom]) 0 50)
            ]
           ; reduce [:raio-x :ultrasom] ==> um unico elemento
           (let [hospital-inicial {:espera espera, :raio-x raio-x, :ultrasom ultrasom}
                 hospital-final (reduce transfere-ignorando-erro hospital-inicial vai-para)]
             (= (total-de-pacientes hospital-inicial)
                (total-de-pacientes hospital-final)))))


(defn adiciona-fila-de-espera [[hospital fila]]
  (assoc hospital :espera fila))

(def hospital-gen
  (gen/fmap
    adiciona-fila-de-espera
    (gen/tuple (gen/not-empty (g/generator h.model/Hospital))
               fila-nao-cheia-gen)))

(def chega-em-gen
  "Gerador de chegadas no hospital"
  (gen/tuple (gen/return chega-em)
             (gen/return :espera)
             nome-aleatorio-gen
             (gen/return 1)))

(defn adiciona-inexistente-ao-departamento [departamento]
  (keyword (str departamento "-inexistente")))

(defn transfere-gen [hospital]
  "Gerados de transferencias no hospital"
  (let [departamentos (keys hospital)
        departamentos-inexistentes (map adiciona-inexistente-ao-departamento departamentos)
        todos-os-departamentos (concat departamentos departamentos-inexistentes)]
    (gen/tuple (gen/return transfere)
               (gen/elements todos-os-departamentos)
               (gen/elements todos-os-departamentos)
               (gen/return 0))))

(defn acao-gen [hospital]
  (gen/one-of [chega-em-gen
               (transfere-gen hospital)]))

(defn acoes-gen [hospital]
  (gen/not-empty (gen/vector (acao-gen hospital) 1 100)))

; a sacada do tratamento do erro é que
; estamos criando um teste que valida a propriedade do sistema
; indepedentemente de as acoes uma a uma terem sucesso o ufracasso
; inclusive com parametros invalidos
; aqui inclusive voce pode discutir de desativar o schema  e o assertion temporariamente
; para ver se em execucao com ele desativado (se voce desativar em producao)
; vai manter as propriedades mesmo em situacoes de erro. super poderoso.
(defn executa-uma-acao [situacao [funcao param1 param2 diferenca-se-sucesso]]
  (let [hospital (:hospital situacao)
        diferenca-atual (:diferenca situacao)]
    (try
      (let [hospital-novo (funcao hospital param1 param2)]
        {:hospital  hospital-novo
         :diferenca (+ diferenca-se-sucesso diferenca-atual)})
      (catch IllegalStateException e
        situacao)
      ; esse é o caso super especifico e novamente um caso de erro generico que ficamos refens
      ; da situação.mas se a equipe de dev junto com a equipe de negocio decidir que não é na transferencia que
      ; deve ser tratado esse erro, voce poderia sinalizar o erro de outras maneiras
      ; retorno, outras exceptions etc. mas ai caimos na mesma situacao de ter que tratar aqui
      ;... se queremos CRIAR UM FRAMEWORK de geracao automatica de acoes e tratamento de erro
      ; provavelmente voce vai ter um padrao de tratamento de erro no seu sistema
      (catch AssertionError e
        situacao))))

(defspec simula-um-dia-do-hospital-nao-perde-pessoas 50
         (prop/for-all
           [hospital-inicial hospital-gen]
           (let [acoes (gen/generate (acoes-gen hospital-inicial))
                 situacao-inicial {:hospital hospital-inicial, :diferenca 0}
                 total-de-pacientes-inicial (total-de-pacientes hospital-inicial)
                 situacao-final (reduce executa-uma-acao situacao-inicial acoes)
                 total-de-pacientes-final (total-de-pacientes (:hospital situacao-final))]
             ;(println total-de-pacientes-final total-de-pacientes-inicial (:diferenca situacao-final))
             (is (= (- total-de-pacientes-final (:diferenca situacao-final)) total-de-pacientes-inicial)))))
















