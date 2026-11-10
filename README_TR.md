# JARVIS Android 0.3.0 — deneme sürümü

**Ne değişti?**
- "Hey Jarvis" yerine yalnızca **"Jarvis"** sözünü algılaması için topluluk tarafından eğitilmiş `jarvis_v1.onnx` kullanılıyor.
- Başlat / durdur / **Sesli yanıtı test et** kontrolleri eklendi.
- Ses algılama motorunun son çalışma durumu "Tanılama" alanında gösteriliyor.
- JARVIS ekranı açık ve görünürken algılama olursa Android varsayılan sesli asistana aktarımı dener.
- Ekran kilitliyken güvenlik kısıtları nedeniyle **başka bir uygulama otomatik olarak açılmaz**; kullanıcı bildirim üzerinden ChatGPT'ye geçebilir.

**Bu, ekran kilitliyken ChatGPT sesli modunu başlatabilen bir uygulama değildir.**
Android standart uygulamaların kilitli/arka plan ekranından başka bir uygulamanın etkinliğini otomatik açmasını kısıtlar. ChatGPT için güvenilir, kamuya açık bir üçüncü taraf "sesli modu şimdi başlat" API'si bulunmadığından bu işlemi garanti etmiyoruz.

## Telefonda sıfırdan test
1. Önceki 0.2.x debug sürümünü kaldırın. GitHub Actions debug derlemelerinin imzası farklı olabilir; yükleme/güncelleme başarısızsa eskisini kaldırın.
2. Actions sekmesindeki son **başarılı** derlemeden `JARVIS-Android-APK` dosyasını indirin, ZIP'i açıp `JARVIS-Hey-Jarvis-debug.apk` dosyasını Android telefona yükleyin.
3. JARVIS'i açın, mikrofon ve bildirim izinlerini verin.
4. **Dinlemeyi başlat** düğmesine basın.
5. **Sesli yanıtı test et** düğmesine basın; "Buyurun efendim" demeli. Bu test başarısızsa ses sentezi / Türkçe ses paketi ayarlarını kontrol edin.
6. Uygulama açıkken normal ses tonuyla tek kelime **"Jarvis"** deyin. Ekranda "JARVIS algılandı!" ve güven skoru yazmasını bekleyin. Android'de ChatGPT varsayılan asistan değilse ChatGPT uygulamasına geçiş deneyimi sesli moda otomatik geçmeyebilir.
7. Telefonu kilitleyip tekrar "Jarvis" deyin. Android marka/model/pil yönetimi politikası nedeniyle servis mikrofona erişimini kaybedebilir.
8. Sonucu ve JARVIS ekranındaki **Tanılama** metnini paylaşın. Ekranda aktif mikrofon simgesi yoksa uygulamanın arka plan/izin ayarlarını kontrol edin.

## ChatGPT ayarları
ChatGPT -> Ayarlar -> Ses -> **Sesle Başlat (Start with Voice)** etkinleştirin (bu seçenek mevcutsa). Android -> Varsayılan uygulamalar -> **Dijital Asistan** olarak ChatGPT'yi seçin (telefonunuzda destekleniyorsa). Bunlar, ekran kilidini aşmaz.

## Kaynak ve lisans
openWakeWord Android kaynak kodu Apache-2.0 ve yerleşik modeller CC BY-NC-SA 4.0 ile lisanslıdır.
Tek sözcüklü model: [fwartner/home-assistant-wakewords-collection](https://github.com/fwartner/home-assistant-wakewords-collection/tree/main/en/jarvis), proje MIT lisanslı. `jarvis_v1.onnx` modelinin raporlanan duyarlılığı sınırlıdır, yanlış/kaçırılmış algılama olabilir. `jarvis_v2` daha az yanlış pozitif ama daha düşük yakalama oranına sahiptir. Konuşma ve seslenme testleri gerçek telefon olmadan doğrulanamaz.

Bir sorun olursa: GitHub Actions derleme kaydı, cihaz üreticisi, Android sürümü, uygulama tanılama mesajı.
