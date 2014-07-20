(ns dynamodb-spike
  (:require [taoensso.faraday :as far]
            [taoensso.nippy :refer [freeze thaw]]
            [clojure.pprint :refer [pprint]]))

; First off, we define credentials for our DynamoDB account.
; We will use this for subsequent operations. Note that
; DynamoDB Local simply ignores the AWS creds.
(def client-opts
  {:access-key "<AWS_DYNAMODB_ACCESS_KEY>"
   :secret-key "<AWS_DYNAMODB_SECRET_KEY>"
   :endpoint   "http://localhost:8000"})

; Let's validate that we can now talk to the DB - we
; will list tables, which will at this point return an
; empty list
(far/list-tables client-opts)
; => ()

; Core data model concepts are simple.
; A database is a collection of tables...
; ... which are a collection of items...
; ... which are a collection of attributes...
; ... which have a 64KB size limit and must use UTF-8.

; Let's now create our first table. DynamoDB requires
; us to specify read and write capacity up-front, though
; this can be changed later. As a guide, 10 units of Write
; Capacity is enough to do up to 36,000 writes per hour,
; and 50 units of Read Capacity are enough for up to 180,000
; strongly consistent reads, or 360,000 eventually
; consistent reads per hour. DynamoDB Local simply ignores
; these settings.
(far/create-table
  client-opts
  :product-catalog                 ; Table name
  [:id :n]                         ; Primary key named "id", (:n => number type)
  {:throughput {:read 10 :write 5} ; Read & write capacity (units/sec)
   :block?     true})              ; Block thread during table creation

; Each table must have a primary key. There are two types,
; 1) Hash and 2) Hash and Range.
; The former uses a hash attribute to build an unordered
; hash index. Unordered indices speed up equality queries,
; e.g. 'get me (the hash of) this attribute'. Not unlike
; a hashtable.

; Let's add our first record. DynamoDB is schema-less, so
; although we only explicitly specified the primary key at
; table creation time, we can add items with other
; attributes too. Let's add a book with a title and authors.
(far/put-item client-opts :product-catalog
              {:id 101
              :title "Book 101 Title"
              :authors ["Author1"]})
; => Exception Unknown DynamoDB value type: class clojure.lang.PersistentVector

; Huh? What happend?
; Well, DynamoDB supports an explicit set of scalar
; and three multi-valued data types:
; Scalar types are Number, String and Binary.
; Multi-valued types are String Set, Number Set and Binary Set.
; So no lists. That's fine, a set will do for our needs:
(far/put-item client-opts :product-catalog
              {:id 101
               :title "Book 101 Title"
               :authors #{"Author1"}})
; => nil

; Looks like that worked - let's confirm:
(far/get-item client-opts :product-catalog {:id 101})
; => {:id 101N, :title "Book 101 Title", :authors #{"Author1"}}

; Alright! We're halfway ("CR") to CRUD already.
; Now say we really wanted to store a type that isn't
; natively supported. We can do this by creating its
; binary representation using our favourite flavour of
; structured data encoding, e.g Thrift or Protocol Buffers.
; Here we've used nippy:
(far/put-item client-opts :product-catalog
              {:id 101
               :title "Book 101 Title"
               :authors (freeze [{:first-name "Author"
                                  :last-name "One"}])})
; => nil

; Main thing to remember is that binary data needs to be
; Base64 encoded. Nippy works the magic for us for free.
; Let's confirm that things worked:
(-> (far/get-item client-opts :product-catalog {:id 101})
    :authors
    thaw)
; => [{:last-name "One", :first-name "Author"}]

; As an aside, note how our last put-item command happily
; overwrote the alread-existing record.
; Had we wanted to update



; Hash and Range - Unordered indices do not speed up

; Schemas upfront but can change attributes

; Aside from requiring a primary key, a DynamoDB table is
; schemaless. Individual items can have any number of
; attributes. We need to keep in mind the 64KB limit on
; item size however.
(far/put-item client-opts :my-table
              {:id 0
               :big-attr (apply str (repeat 100000 "x"))})
; AmazonServiceException Item size is limited to 64 kB, including attribute names (Service: AmazonDynamoDBv2; Status Code: 400; Error Code: ValidationException; Request ID: e8abbaa4-f180-40e1-8d8b-95e34ce0bb51)  com.amazonaws.http.AmazonHttpClient.handleErrorResponse (AmazonHttpClient.java:805)

; TODO
; Update table
; Update indices

; Designing for uniform access across tables
; DynamoDB divides tables into multiple partitions when storing data,
; and distributes data across these primarily by hash key element.
; If a small number of your data items accounds for the bulk of access,
; or if your data hashes non-uniformly in such a way as to


; Geospatial

(defn add-more-product-items
  (->
    [{:id 101
      :title "Book 101 Title"
      :isbn "111-1111111111"
      :authors #{"Author1"}
      :price 2
      :dimensions "8.5 x 11.0 x 0.5"
      :page-count 500
      :in-publication 1
      :product-category "Book"}

     {:id 102
      :title "Book 102 Title"
      :isbn "222-2222222222"
      :authors ["Author1", "Author2"]
      :price 20
      :dimensions "8.5 x 11.0 x 0.8"
      :page-count 600
      :in-publication 1
      :product-category "Book"}

     {:id 103
      :title "Book 103 Title"
      :isbn "333-3333333333"
      :authors ["Author1", "Author2"]
      ; Intentional. Later we run scan to find price error. Find items > 1000 in price.
      :price 2000
      :dimensions "8.5 x 11.0 x 1.5"
      :page-count 600
      :in-publication 0
      :product-category "Book"}

     ; Add bikes.
     {:id 201
      :title "18-Bike-201"  ; Size, followed by some title.
      :description "201 Description"
      :bicycle-type "Road"
      :brand "Mountain A"  ; Trek, Specialized.
      :price 100
      :gender "M"  ; Men's
      :colors ["Red", "Black"]
      :product-category "Bicycle"}

     {:id 202
      :title "21-Bike-202"
      :description "202 Description"
      :bicycle-type "Road"
      :brand "Brand-Company A"
      :price 200
      :gender "M"
      :colors ["Green", "Black"]
      :product-category "Bicycle"}

     {:id 203
      :title "19-Bike-203"
      :description "203 Description"
      :bicycle-type "Road"
      :brand "Brand-Company B"
      :price 300
      :gender "W"  ; Women's
      :colors ["Red", "Green", "Black"]
      :product-category "Bicycle"}

     {:id 204
      :title "18-Bike-204"
      :description "204 Description"
      :bicycle-type "Mountain"
      :brand "Brand-Company B"
      :price 400
      :gender "W"
      :colors ["Red"]
      :product-category "Bicycle"}

     {:id 205
      :title "20-Bike-205"
      :description "205 Description"
      :bicycle-type "Hybrid"
      :brand "Brand-Company C"
      :price 500
      :gender "B"  ; Boy's
      :colors ["Red", "Black"]
      :product-category "Bicycle"}])
  (map (partial far/put-item client-opts :product-catalog))
  (dorun))