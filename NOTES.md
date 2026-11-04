# Load Balancer Service Registry — Öğrenim Notları

Bu dosya, katayı TDD ile çözerken (v1) ve thread-safe hâle getirirken (v2) öğrendiklerimin özetidir.

---

## Bölüm 1 — TDD (v1)

### Döngü
1. 🔴 **Red:** Önce test. Testin **doğru sebepten** kırmızı olduğunu gör (hata mesajını oku).
2. 🟢 **Green:** Testi geçirecek **en basit** kodu yaz. Testin istemediği kodu yazma (YAGNI).
3. 🔵 **Refactor:** Testler yeşilken temizle.
4. Her değişiklikten sonra **tüm** testleri koştur (regresyonlar böyle yakalanır — örn. boş registry'de `% 0`).

### Dersler
- **Assertion'sız test her zaman yeşildir.** Hiçbir şey kanıtlamaz.
- **Kod testten önce yazıldıysa → mutation check:** Kodu geçici bozan değişikliği yap (satırı yorumla), testin kırmızıya döndüğünü gör, geri al.
- **Davranışı public API üzerinden test et.** Test geçsin diye API'yi esnetme (`getAll()` gibi). Dönen iç koleksiyon encapsulation sızıntısıdır (`clear()`, `add()` ile kurallar delinir).
- **Kontratı testi geçirmek için değiştirme** (`void` → `boolean` gibi).
- **Ortamı en başta düzelt:** pom (surefire 3.x, Java 17+), eski JUnit 3 testleri vs.
- Her yeşilde küçük commit → red/green ritmi görünür.

### Tasarım kararları
| Konu | Karar | Neden |
|---|---|---|
| Geçersiz adres (null/blank) | `IllegalArgumentException` | Sorun argümanda |
| Duplicate adres | `IllegalArgumentException` | Argüman bu durumda kabul edilemez |
| Limit aşımı / boş registry | `IllegalStateException` | Sorun registry'nin **durumunda** |
| Kontrol sırası | Önce argüman doğrulama (fail fast), sonra limit, sonra duplicate | Bozuk argüman varsa durum önemsiz |
| Exception tipi | Spesifik, **unchecked**; açıklayıcı mesaj | `throws Exception` çağıranı boşuna zorlar, bilgi vermez |
| Ayrı exception tipleri | Testin hangi kuralın ihlal edildiğini ayırt edebilmesi için | Aynı tip → test yanlış sebepten yeşil olabilir |
| Adres normalizasyonu | `trim()` edilip saklanıyor | `" a "` ile `"a"` duplicate sayılır |
| `isBlank()` | `trim().isEmpty()` yerine | Daha okunur, yeni String üretmez |
| `LinkedHashSet` | Ekleme sırasını korur | Round-robin sırası (a, b, c, a) için şart |
| `new HashSet<>(10)` | **Limit değildir** | Sadece initial capacity |
| Round-robin index | `Math.floorMod(counter, size)` | Sayaç taştığında negatif mod'dan korur |
| Random index | `random.nextInt(size)` | `nextInt(10) % size` → **modulo bias** (ilk elemanlar daha çok seçilir) |
| `get()` / `getRandom()` | Ayrı metotlar (YAGNI) | 3. strateji veya runtime değişimi gelirse → Strategy pattern (kompozisyon) |

### Kalıtım vs Strategy
- Kalıtım: "RoundRobin **bir** registry'dir" → strateji değiştirmek için yeni registry + veri taşıma.
- Strategy (kompozisyon): "Registry'nin **bir seçim stratejisi vardır**" → runtime'da değiştirilebilir, Open/Closed.
- Mülakat cevabı: *"Şimdilik basit tutuyorum; 3. strateji gelirse seçim mantığını bir Strategy interface'ine çıkarırım."*

### Küçük ama önemli
- Lambda içinde döngü değişkeni kullanılamaz (effectively final değil) → turda yerel kopya: `final int n = i;`
- `assertEquals(1, atomicInteger)` → yanlış! `AtomicInteger` `equals`'ı override etmez → `.get()` kullan.
- Rastgele davranışı test ederken: döngüde çok kez çağır, her sonucun kayıtlı olduğunu kontrol et.
- `@ParameterizedTest` + `@ValueSource` / `@NullAndEmptySource` → `junit-jupiter-params` bağımlılığı gerekir.
- Parantezsiz `if` kullanma.

---

## Bölüm 2 — Concurrency (v2)

### Check-then-act
"Önce kontrol et, sonra sonuca göre işlem yap." İki adım arasına başka thread girerse kontrolün sonucu geçersiz olur.
- `if (size == 10) throw; add(...)` → 2 thread aynı anda 9'u görür → 11 kayıt.
- `HashMap.put` içinde: "kova boş mu? → yaz" → 2 thread de "eklendi" der.
- **Çözüm:** Kontrol + işlem tek bir kilit altında atomik olmalı.

### Lock leak ≠ Deadlock
- **Lock leak:** Thread kilidi alır, exception fırlar, `unlock()` hiç çalışmaz → herkes sonsuza kadar bekler.
- **Deadlock:** A 1. kilidi tutup 2.'yi bekler, B 2.'yi tutup 1.'yi bekler. En az **iki** kilit gerekir.
- **Tek kilit** hem karşılıklı dışlamayı sağlar hem deadlock riskini ortadan kaldırır.

### Neden tek kilit?
Farklı kilitler birbirini engellemez. `register` `synchronized(set)`, `get` `ReentrantLock` kullanınca CME devam etmişti. **Aynı kaynağa dokunan bütün metotlar aynı kilidi kullanmalı.**

### synchronized vs ReentrantLock
| | synchronized | ReentrantLock |
|---|---|---|
| Exception'da kilit bırakma | Otomatik | **Sen** (`finally`) |
| `tryLock(timeout)` | Yok | Var — deadlock'tan kaçış |
| Fairness | Yok | `new ReentrantLock(true)` |
| Kesilebilir bekleme | Yok | `lockInterruptibly()` |

Doğru kalıp:
```java
lock.lock();          // try'ın HEMEN önünde
try {
    ...               // return burada olabilir; finally yine çalışır
} finally {
    lock.unlock();    // finally'de sadece temizlik
}
```
- Validation kilidin **dışında** (paylaşılan veriye dokunmuyor → kilidi gereksiz tutma).
- Kilit alanı her zaman `final`.

### finally kuralları
- `try` içinden `return` edilse bile `finally` **çalışır** (Java garantisi).
- `catch` yazmazsan exception olduğu gibi çağırana gider.
- **`finally` içinde `return` yazma:** `try`'da fırlayan exception'ı **siler**, metot normal dönmüş gibi görünür → bug gizlenir. `finally` = sadece temizlik (unlock, close).
- `catch (Exception e) {}` → bug'ı düzeltmek değil, halının altına süpürmek.

### Okuma maddeleri — tek cümlelik cevaplar
- **AtomicInteger:** Tek bir sayaç kilitsiz paylaşılıyorsa yeterli (oku-artır-yaz tek atomik işlem + görünürlük). Kilit içinde gereksizdir (kilit zaten atomiklik + görünürlük verir), ama ileride kilitsiz kullanıma karşı güvenli kalsın diye tutulabilir.
- **Taşma:** `int` ve `AtomicInteger` aynı şekilde taşar (`MAX + 1 = MIN`) → `floorMod` her ikisinde de çalışır.
- **volatile:** Görünürlük sağlar, **atomiklik sağlamaz** (`counter++` hâlâ 3 adım).
- **ConcurrentHashMap:** Tek tek işlemleri atomik yapar; ama "size < 10 ise ekle" **koleksiyon geneli** bir kural, iki işlemdir. `computeIfAbsent` sadece **o anahtar** için atomik. Ayrıca sıra tutmaz (round-robin'e uymaz).

### ReentrantReadWriteLock
- `readLock`: birden fazla okuyucu aynı anda; `writeLock`: tek yazar, okuyucular da bekler.
- `register` → writeLock, `get`/`getRandom` → readLock.
- **En önemli kural:** readLock altındaki kod paylaşılan **hiçbir** durumu değiştirmemeli; değiştiriyorsa ya writeLock kullan ya da o durum kendi başına thread-safe olsun.
  - Tuzak: `get()` set'i değiştirmiyor ama **sayacı** değiştiriyor → readLock altında `AtomicInteger` **zorunlu** olur.
- Read → write **upgrade yapılamaz** (kendi kendini kilitler). Write → read downgrade olur.
- Writer starvation → fair mod (`new ReentrantReadWriteLock(true)`).
- Çok thread'li random → `ThreadLocalRandom` (paylaşılan `Random` contention yaratır).

---

## Bölüm 3 — Concurrency testi yazmak

### Latch kalıbı
```java
CountDownLatch ready = new CountDownLatch(n);  // herkes hazır mı?
CountDownLatch start = new CountDownLatch(1);  // başlangıç tabancası
CountDownLatch done  = new CountDownLatch(n);  // herkes bitti mi?

executor.execute(() -> {
    ready.countDown();
    try {
        start.await();          // herkes burada bekler
        ... iş ...
    } catch (BeklenenException e) {
        sayaç.incrementAndGet();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();   // flag'i geri set et
    } finally {
        done.countDown();       // exception olsa bile
    }
});

ready.await();
start.countDown();              // hepsini aynı anda bırak
assertTrue(done.await(5, TimeUnit.SECONDS));   // timeout → donma yerine kırmızı
executor.shutdown();
// assert'ler BURADA, ana thread'de
```

### Kurallar
- **Worker thread'de assert yazma.** `AssertionError` executor thread'inde kaybolur; JUnit sadece ana thread'i izler. Worker'lar sadece **kaydeder** (thread-safe sayaç), ana thread **assert eder**.
- **Bozulmayı dışarıdan ölç:** İçeriye bakmak yerine sözleşmeye bak. "100 thread aynı adresi kaydetti → **tam 1** başarı olmalı." 2 başarı = bug.
- **İki sayaç:** başarı + red → `başarı + red == n` hiçbir thread'in kaybolmadığını kanıtlar. `assertAll` ile hepsini birden gör.
- **Yakalanmayan exception kaybolur** → beklenen exception'ları kendi `catch`'inde, beklenmeyenleri `RuntimeException` ile say.
- **Aynı exception farklı bağlamda farklı anlam taşır:** Yazardan gelen ISE (limit) = beklenen; okuyucudan gelen ISE = bug. "Hangi exception?" değil, **"kimden, hangi bağlamda?"** diye sor.
- **Test istenen davranışı anlatır:** `assertTrue(cme > 0)` bug'ın var olduğunu iddia eder → yanlış. `assertEquals(0, okuyucuHataları)` → doğru.
- **Tek seferlik yeşil hiçbir şey kanıtlamaz:** `@RepeatedTest(200)`. Race penceresi nanosaniyeler → bazen patlar (flaky = race imzası).
- **Mutation check:** Kilidi geçici kaldır → kırmızı, geri koy → yeşil. Senkronizasyonun gerçekten işe yaradığının kanıtı.
- Okuma/yazma yarışında okuyucuyu döngüde çok kez çağır (çakışma ihtimali artar); başta registry'yi doldur (boş registry durumu karışmasın).
- `wait()` ≠ `await()`: `wait()` Object'in eski bekleme metodu; latch'te `await()`.
- `execute(Runnable)` parametre almaz → lambda capture. Sonuç döndürmek için `submit(Callable)` → `Future`.
- **Paylaşılan + değişen veri** → senkronizasyon/atomic. **Paylaşılmayan veya değişmeyen** → düz tip (immutability en güvenli concurrency).

---

## Mülakat taktikleri
- Her kararı **tek cümleyle** gerekçelendir (exception tipi, kontrol sırası, YAGNI vs Strategy).
- Kırmızıyı göster, hata mesajını sesli oku.
- Edge case'leri kendin söyle: dolu registry + duplicate adres hangi exception'ı alır? Sayaç taşınca ne olur?
- Hedef süre: 45 dk. Ortamı ilk 2 dakikada hazırla.
