## v1.6.0 / 2014 May 8

 * **NEW**: `ajax-lite` now returns XHR instance on success.


## v1.5.1 / 2014 May 7

 * [#1] **FIX**: missing tools.reader dependency (@ducky427).
 * **NEW**: Add `qbench` macro.


## v1.5.0 / 2014 May 2

 * **NEW**: Validation utils.
 * **NEW**: Add `nvec?`.
 * **FIX**: `nblank-str?`.
 * **FIX**: `interleave-all`.


## v1.4.0 / 2014 Apr 17

 * **NEW**: Add `nblank-str?`.
 * **BREAKING**: Further simplify `swap-in!`, add support for arbitrary return values. See commit for details.


## v1.3.1 / 2014 Apr 10

 * **FIX** broken Clojure <1.5 support by using correct (tools.reader) edn reader (@kul).


## v1.3.0 / 2014 Apr 8

 * Housekeeping: `swap!` stuff.
 * Rewrote memoization utils for lock-free anti-contention (better performance under high contention).


## v1.2.1 / 2014 Apr 1

 * **FIX**: `ajax-lite` catch typo.
 * **FIX**: `ajax-lite` wrong (auto) response type for `text/html`.


## v1.2.0 / 2014 Mar 30

 * **NEW**: `compiling-cljs?` utils.
 * **FIX**: nix accidental `declare-remote` println.


## v1.1.0 / 2014 Mar 28

 * **NEW**: `swap-in!`, `reset-in!`, `dissoc-in`, `contains-in?`, `assoc-some`, `assoc-when` (all cross-platform).


## v1.0.0 / 2014 Mar 26

 * Minor housekeeping, **v1.0.0 release**.


## v0.9.9 / 2014 Mar 15

 * **NEW**: Add `swap!*`, `reset!*` fns (cross-platform).
 * **NEW**: Add `substr` fn (cross-platform).
 * **DEPRECATED**: `str-trunc` (`substr` is a more general form).


## v0.9.8 / 2014 Mar 10

 * **FIX**: `ajax-lite` broken response type detection.


## v0.9.7 / 2014 Mar 8

 * **NEW**: Add `pow` fn (cross-platform).
 * **NEW**: Add some Ring utils (clj only).


## v0.9.6 / 2014 Mar 7

 * **NEW**: Add `simple-date-format` util (clj only).


## v0.9.5 / 2014 Mar 6

 * **NEW**: Add clj-side `format` fn for easier cross-platform use.
 * **NEW**: Add clj(s)-side `nblank?` fns.
 * **NEW**: Add `round*` alias.
 * **NEW**: Add `now-udt-mock-fn`, `make-timestamp-fn`.


## v0.9.4 / 2014 Feb 28

 * **FIX**: Faulty `str-trunc` (clj). Regression from v0.9.1.
 * **NEW**: Add separate parse-/as- coercion fns.


## v0.9.3 / 2014 Feb 27

 * **FIX**: `as-map` post-condition: can return nil.


## v0.9.2 / 2014 Feb 26

 * **FIX**: project.clj to prevent unnecessary downstream deps.


## v0.9.1 / 2014 Feb 25

 * **FIX**: Faulty cljs utils: `ajax-lite`, `str-ends-with?`, `str-trunc`.


## v0.9.0 / 2014 Feb 24

 * **NEW**: Added simple Closure-based client Ajax util for use by Sente.


## v0.8.0 / 2014 Feb 23

 * **NEW**: Initial public release.
