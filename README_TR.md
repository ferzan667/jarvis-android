# JARVIS Android 0.4.0 — deneme sürümü

**Ne değişti?**
- "Hey Jarvis" yerine yalnızca **"Jarvis"** sözünü algılaması için topluluk tarafından eğitilmiş `jarvis_v1.onnx` kullanılıyor.
- Başlat / durdur / yalnızca elle çalışan **TTS tanı testi** kontrolleri var. "Jarvis" algılanınca JARVIS sesli yanıt vermez.
- Ses algılama motorunun son çalışma durumu "Tanılama" alanında gösteriliyor.
- Algılama skoru ve oturum içi algılama sayısı gösteriliyor. Algılayıcı kütüphanesi mikrofonu ayrı iş parçacığında açtığı ve hata geri çağrısı sunmadığı için ilk "başlatıldı" mesajı gerçek mikrofon doğrulaması sayılmaz.
- Algılama denemesi için eşik 0,35. Yanlış tetikleme artabilir; cihaz ölçümüyle ayarlanmalıdır.
- Sesli yanıt testi dinleme servisinden bağımsız çalışır. Mikrofon kaydı etkin/etkin değil bilgisi Android 10+ cihazlarda gösterilir.
- ChatGPT Android varsayılan asistanıysa JARVIS algılamadan sonra resmî `ACTION_ASSIST` çağrısını dener. Uygulama görünürken bu çağrı asistanı açabilir.
- Ekran kilitliyken aynı çağrı deneysel olarak istenir. Android arka plandan etkinlik açmayı engelleyebilir; isteğin gönderilmiş olması ChatGPT Voice'un açıldığını doğrulamaz. Bildirim yedek geçiştir.
- Kullanıcı başlattıktan sonra mikrofon servisi ön planda kalır. Algılama sonrası mikrofonu ChatGPT'ye bırakır; başka kayıt kalmayınca dinlemeyi yeniden başlatır. `START_STICKY` Android öldürdüğünde yeniden oluşturmayı isteyebilir; Realme veya Android bunu garanti etmez.

**Kilit ekranından ChatGPT Voice'un otomatik başlaması henüz doğrulanmadı.**
Android standart uygulamaların kilitli/arka plan ekranından başka bir uygulamanın etkinliğini otomatik açmasını kısıtlar. ChatGPT için kamuya açık bir üçüncü taraf "sesli modu şimdi başlat" API'si bulunmadığından bu işlemi garanti etmiyoruz.

## Telefonda sıfırdan test
1. Önceki 0.2.x debug sürümünü kaldırın. GitHub Actions debug derlemelerinin imzası farklı olabilir; yükleme/güncelleme başarısızsa eskisini kaldırın.
2. [Releases](https://github.com/ferzan667/jarvis-android/releases) altında 0.4.0 varsa `JARVIS-debug.apk` dosyasını doğrudan indirin. Yoksa [Actions](https://github.com/ferzan667/jarvis-android/actions) sekmesindeki son **başarılı** derlemeden `JARVIS-Android-APK` ZIP'ini indirip içindeki APK'yı yükleyin.
3. JARVIS'i açın, mikrofon ve bildirim izinlerini verin.
4. İsterseniz **TTS tanı testi** düğmesine basın; bu yalnızca elle ses üretimini sınar. Jarvis algılandığında bu ses çalınmaz.
5. **Dinlemeyi başlat** düğmesine basın. Tanılama altında **Mikrofon kaydı: ses tanıma kaydı etkin** görünmeli (Android 10+). "Görünmüyor" ise mikrofon veya izin sorunu olabilir. Bu gösterge Android'in etkin kayıt listesine bakar; tek başına JARVIS modelinin çalıştığını kanıtlamaz.
6. Uygulama açıkken normal ses tonuyla tek kelime **"Jarvis"** deyin. Ekranda "JARVIS algılandı!" ve skor yazmasını bekleyin. ChatGPT asistan arayüzü açılıyor mu, doğrudan Voice başlıyor mu ayrı ayrı not edin.
7. Telefonu kilitleyip tekrar "Jarvis" deyin. ChatGPT asistan açılışı ve Voice başlangıcını ayrı ayrı not edin. Android arka plan kuralı çağrıyı engelleyebilir. Servis bildirimde sürmeli; görüşme bitince dinleme yeniden başlamalı.
8. Sonucu ve JARVIS ekranındaki **Tanılama** metnini paylaşın. Ekranda aktif mikrofon simgesi yoksa uygulamanın arka plan/izin ayarlarını kontrol edin.

### Logcat ile mikrofon ayrımı

Bilgisayara ADB bağlıysa `adb logcat -s Jarvis OpenWakeWord` komutunu çalıştırın. `Models loaded` modelin açıldığını, `Failed to initialize AudioRecord` mikrofon açılamadığını, `Error in processing thread` çıkarım hatasını gösterir. Ekrandaki skor yalnızca eşik aşıldığında gelir; kütüphane sürekli skor veya ses seviyesi API'si sunmuyor. Uygulama ses kaydı saklamaz. Test için 10 kez "Jarvis" deyip kaçının algılandığını ve 10 dakikalık sessizlikte yanlış tetikleme sayısını not edin; sonra aynı testi kilitli ekranda yapın.

## ChatGPT ayarları
ChatGPT -> Ayarlar -> Ses -> **Sesle Başlat (Start with Voice)** etkinleştirin (bu seçenek mevcutsa). [OpenAI'nin resmî belgesine](https://help.openai.com/en/articles/20001274-chatgpt-voice) göre bu, ChatGPT yeni veya boş sohbete açıldığında Voice'u başlatır. Mevcut bir Voice görüşmesi kilitli ekranda sürdürülebilir; üçüncü taraf uygulamanın kilit ekranından yeni Voice görüşmesi başlatması için belgelenmiş bir arayüz yoktur.

## Kaynak ve lisans
openWakeWord Android kaynak kodu Apache-2.0 ve yerleşik modeller CC BY-NC-SA 4.0 ile lisanslıdır.
Tek sözcüklü model: [fwartner/home-assistant-wakewords-collection](https://github.com/fwartner/home-assistant-wakewords-collection/tree/main/en/jarvis), proje MIT lisanslı. `jarvis_v1.onnx` modelinin raporlanan duyarlılığı sınırlıdır, yanlış/kaçırılmış algılama olabilir. `jarvis_v2` daha az yanlış pozitif ama daha düşük yakalama oranına sahiptir. Konuşma ve seslenme testleri gerçek telefon olmadan doğrulanamaz.

Bir sorun olursa: GitHub Actions derleme kaydı, cihaz üreticisi, Android sürümü, uygulama tanılama mesajı.

## Realme 11 arka plan ayarı

JARVIS'i ekranda açıp dinlemeyi **bir kez** başlatın. Sonra Realme ayarlarında **Ayarlar → Pil → Uygulama pil yönetimi → JARVIS → Arka plan etkinliğine izin ver** seçeneğini açın. Realme'nin [resmî destek sayfası](https://www.realme.com/global/support/faq) ayrıca ön plan etkinliği ve otomatik başlatma izinlerini öneriyor. Bu ayarlar Android'in kilit ekranı veya başka uygulama açma kurallarını kaldırmaz. Sürekli mikrofon dinlemesi pil tüketir ve Android mikrofon göstergesi görünür.



