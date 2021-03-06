(ns live-demo
  (:import [javax.sound.midi MidiSystem ShortMessage]))

;; List available devices
(for [device-info (MidiSystem/getMidiDeviceInfo)]
  [(.getName device-info)
   (.getDescription device-info)])

(defn get-midi-device
  [name]
  (when-let [device-info (last (filter #(= name (.getName %))
                                       (MidiSystem/getMidiDeviceInfo)))]
    (MidiSystem/getMidiDevice device-info)))

;; Play a note from the Gervill device (Java built-in)
(comment
  (with-open [device (doto (get-midi-device "Gervill") .open)]
    (let [receiver (.getReceiver device)]
      (.send receiver (doto (ShortMessage.)
                        (.setMessage ShortMessage/NOTE_ON 60 127))
             -1)
      (Thread/sleep 2000)
      (.send receiver (doto (ShortMessage.)
                        (.setMessage ShortMessage/NOTE_OFF 60 127))
             -1)))
  )

;; Play a seq of notes
(defn play-notes
  [receiver notes]
  (doseq [note notes]
    (when note
      (.send receiver
             (doto (ShortMessage.)
               (.setMessage ShortMessage/NOTE_ON note 127))
             -1))
    (Thread/sleep 200)
    (when note
      (.send receiver
             (doto (ShortMessage.)
               (.setMessage ShortMessage/NOTE_OFF note 0))
             -1))
    (Thread/sleep 200)))

(comment
  (with-open [device (doto (get-midi-device "Gervill") .open)]
    (play-notes (.getReceiver device) [60 64 67 71]))
  )

;; Try your favourite seq functions!
(comment
  (with-open [device (doto (get-midi-device "Gervill") .open)]
    (play-notes
     (.getReceiver device)
     (take
      30
      (cycle
       (concat [60 64 67 nil 71]
               (reverse [60 64 nil 67 71])
               (shuffle [60 64 67 71]))))))
  )

;; It's more fun to play with when it is looping
(defonce melody
  (atom nil))

(defn start-player
  [a]
  (future
    (with-open [device (doto (get-midi-device "Gervill") .open)]
      (let [receiver (.getReceiver device)]
        (while @a
          (play-notes receiver @a))))))

(comment
  (start-player melody)

  (reset! melody [60 64 67 71])

  (swap! melody reverse)
  (swap! melody shuffle)

  (reset! melody [60 60 60 nil 64 67 67 71])

  (reset! melody nil)
  )

;; The notes from before were not completely random...
(comment
  (start-player melody)

  (reset! melody (random-sample 0.4 (range 60 80)))
  (reset! melody (random-sample 0.4 (concat (range 60 80) (repeat 5 nil))))

  (swap! melody shuffle)
  (swap! melody reverse)

  (reset! melody nil)
  )

;; Let's play notes in a scale
(defn midi-notes
  [tone-pattern offset]
  (->> tone-pattern
       (map {:semitone 1
             :tone 2})
       cycle
       (cons offset)
       (take 127)
       (reductions +)
       (filter (partial > 128))))

(def major-scale
  [:tone :tone :semitone :tone :tone :tone :semitone])
(def locrian-mode
  [:semitone :tone :tone :semitone :tone :tone :tone])

(comment
  (start-player melody)

  (reset! melody (filter #(< 50 % 75) (midi-notes major-scale 0)))

  (reset! melody (->> (midi-notes locrian-mode 5)
                      (filter #(< 50 % 75))
                      (concat (repeat 5 nil))
                      cycle
                      (random-sample 0.1)
                      (take 10)))

  (let [notes (->> (midi-notes locrian-mode 5)
                   (filter #(< 50 % 75))
                   (concat (repeat 5 nil))
                   cycle)
        a (->> notes (random-sample 0.1) (take 4))
        b (->> notes (random-sample 0.1) (take 8))]
    (reset! melody (concat a a b)))

  (swap! melody shuffle)
  (swap! melody reverse)

  (reset! melody nil)
  )

;; Hooking up to Helm!
(comment
  (defn start-player
    [a]
    (future
      (with-open [device (doto (get-midi-device "Bus 1") .open)]
        (let [receiver (.getReceiver device)]
          (while @a
            (play-notes receiver @a))))))
  )

;; Let's try out the edit distance
(require 'edit-distance)

(comment
  (edit-distance/edit-trace  "cats" "hat")

  (start-player melody)

  (let [notes (->> (midi-notes locrian-mode 5)
                   (filter #(< 50 % 75))
                   (concat (repeat 5 nil))
                   cycle)
        a (->> notes (random-sample 0.1) (take 4))
        d (->> notes (random-sample 0.1) (take 4))
        t (edit-distance/edit-trace a d)
        [i j] (sort [(rand-int (count t)) (rand-int (count t))])
        b (nth t i)
        c (nth t j)]
    (reset! melody (concat a b c d)))

  (reset! melody nil)
  )

;; Euclidian rhythms
(require 'euclidian-rhythms)

(comment
  (euclidian-rhythms/euclidian-pattern 16 4 :foo)
  (euclidian-rhythms/euclidian-pattern 16 4 60)
  (euclidian-rhythms/euclidian-pattern 16 3 63)

  (start-player melody)

  (reset! melody (euclidian-rhythms/euclidian-pattern 16 4 60))

  (reset! melody nil)
  )

;; Multiple notes at once (polyphonic)
(defn shift
  [n melody]
  (take (count melody) (drop n (cycle melody))))

(defn play-polyphonic-notes
  [receiver notes-seq]
  (doseq [notes notes-seq]
    (doseq [note notes]
      (when note
        (.send receiver
               (doto (ShortMessage.)
                 (.setMessage ShortMessage/NOTE_ON note 127))
               -1)))
    (Thread/sleep 200)
    (doseq [note notes]
      (when note
        (.send receiver
               (doto (ShortMessage.)
                 (.setMessage ShortMessage/NOTE_OFF note 0))
               -1)))
    (Thread/sleep 200)))

(defn start-polyphonic-player
  [a]
  (future
    (with-open [device (doto (get-midi-device "Bus 1") .open)]
      (let [receiver (.getReceiver device)]
        (while @a
          (play-polyphonic-notes receiver @a))))))

(comment
  (start-polyphonic-player melody)

  (reset! melody
          (map vector
               (->> (euclidian-rhythms/euclidian-pattern 16 8 60) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern 16 4 64) (shift 2))))

  (reset! melody
          (map vector
               (->> (euclidian-rhythms/euclidian-pattern 16 8 60) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern 16 3 64) (shift 2))
               (->> (euclidian-rhythms/euclidian-pattern 16 2 67) (shift 3))
               (->> (euclidian-rhythms/euclidian-pattern 16 5 71) (shift 0))))

  (reset! melody nil)
  )

;; Hooking up to DM1
(def drum-notes
  [36 37 38 39 42 43 46 49 56])

(comment
  (defn start-polyphonic-player
    [a]
    (future
      (with-open [device (doto (get-midi-device "IAC Bus 2") .open)]
        (let [receiver (.getReceiver device)]
          (while @a
            (play-polyphonic-notes receiver @a))))))
  )

(comment
  (with-open [device (doto (get-midi-device "IAC Bus 2") .open)]
    (play-notes (.getReceiver device) drum-notes))

  (start-polyphonic-player melody)

  (reset! melody
          (map vector
               (->> (euclidian-rhythms/euclidian-pattern
                     16 8 (first drum-notes)) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 4 (second drum-notes)) (shift 2))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 7 (nth drum-notes 2)) (shift 2))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 7 (nth drum-notes 2)) (shift 2))))

  (reset! melody
          (map vector
               (->> (euclidian-rhythms/euclidian-pattern
                     16 8 (first drum-notes)) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 4 (second drum-notes)) (shift 2))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 7 (nth drum-notes 2)) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 5 (nth drum-notes 3)) (shift 1))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 4 (nth drum-notes 5)) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 3 (nth drum-notes 6)) (shift 5))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 16 (nth drum-notes 7)) (shift 0))))

  (reset! melody
          (map vector
               (->> (euclidian-rhythms/euclidian-pattern
                     16 8 (nth drum-notes 0)) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 4 (nth drum-notes 1)) (shift 2))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 9 (nth drum-notes 4)) (shift 3))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 5 (nth drum-notes 3)) (shift 1))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 6 (nth drum-notes 5)) (shift 0))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 7 (nth drum-notes 6)) (shift 3))
               (->> (euclidian-rhythms/euclidian-pattern
                     16 8 (nth drum-notes 7)) (shift 5))
               ))

  (reset! melody nil)
  )
