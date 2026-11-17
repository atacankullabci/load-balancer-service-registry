# load-balancer-service-registry

A service registry with load balancing, built test-first (v1) and then made thread-safe (v2). Plain Java, JUnit 5.

## Requirements

- Register service addresses; reject invalid (null/blank) and duplicate addresses.
- At most 10 addresses.
- Pick an address with **round-robin** or **random** selection.
- v2: `register` and the selection methods can be called from many threads at once.

## Run

```bash
mvn test
```

`ServiceRegistryTest` covers the rules. `ServiceRegistryConcurrencyTest` runs each concurrency scenario 200 times (`@RepeatedTest(200)`), because a race can pass a single run by luck.

## Design decisions

| Topic | Decision | Why |
|---|---|---|
| Invalid / duplicate address | `IllegalArgumentException` | The problem is the argument |
| Limit reached / empty registry | `IllegalStateException` | The problem is the registry's state |
| Check order | Validate the argument first, then the limit, then duplicates | Fail fast on bad input |
| Storage | `LinkedHashSet`, addresses trimmed | Keeps insertion order for round-robin; `" a "` and `"a"` are the same address |
| Round-robin index | `Math.floorMod(counter, size)` | Stays non-negative when the counter overflows |
| Random index | `random.nextInt(size)` | `nextInt(10) % size` has modulo bias |
| Locking (v2) | One lock for all methods that touch the set | "If size < 10 then add" is check-then-act; check and add must be one atomic step. Different locks don't exclude each other |
| `ReentrantLock` vs `synchronized` | One `ReentrantLock`; `lock()` right before `try`, `unlock()` in `finally` | Supports `tryLock(timeout)` and fairness if needed later |
| Round-robin counter | `AtomicInteger` | Safe even if selection later runs outside the lock |

## How the concurrency tests work

- Three `CountDownLatch`es (ready / start / done) release all threads at the same moment.
- Worker threads only count results; all asserts run on the main thread (an `AssertionError` in a worker thread is lost).
- Example contract: 100 threads register the same address → exactly 1 success, 99 rejections, and successes + rejections = 100.
- Mutation check: removing the lock turns the tests red.

## Next steps

- Reads are far more common than writes in a registry: a `ReentrantReadWriteLock` (write lock for `register`, read lock for selection) would let readers run in parallel. The counter already being an `AtomicInteger` is what makes that safe.
- If a third selection strategy is needed, move selection behind a `Strategy` interface instead of subclassing the registry.

My detailed learning notes (in Turkish) are in [NOTES.md](NOTES.md).
