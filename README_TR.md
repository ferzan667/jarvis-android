# JARVIS Android 0.5.0 — Gemini Live denemesi

Bu sürüm, ChatGPT uygulamasına geçmeye çalışmak yerine JARVIS içinde Gemini Live sesli görüşmesi açar. Android uygulaması bilgisayar gerektirmez. İncelenen CanFPV masaüstü ZIP'inden kod, API anahtarı veya TLS özel anahtarı kopyalanmadı.

## Telefonda deneme sırası

1. [Releases](https://github.com/ferzan667/jarvis-android/releases) bölümünden `JARVIS-debug.apk` dosyasını indirip yükle. Önceki debug APK farklı imzalıysa önce kaldırman gerekebilir.
2. Google AI Studio'da kendi Gemini API anahtarını oluştur. Anahtarı sohbete veya GitHub'a gönderme; yalnızca telefondaki JARVIS ekranına yazıp **Anahtarı telefonda kaydet** düğmesine bas. Anahtar Android Keystore ile bu cihazda şifreli saklanır. Sunucusuz istemcide çalışan bir anahtar yine ileri düzey cihaz erişimine karşı tam gizli sayılamaz.
3. Mikrofon iznini ver. Önce **Gemini görüşmesini dene** düğmesine bas. "Gemini Live bağlı" görünmeli ve konuşma/yanıt duyulmalı. Bu test, "Jarvis" algılamasından bağımsızdır.
4. Görüşmeyi bitirip **Dinlemeyi başlat** düğmesine bas. **Mikrofon kaydı** göstergesini kontrol et; sonra "Jarvis" de. Algılanırsa yerel algılayıcı mikrofonu bırakır ve Gemini Live görüşmesi başlar.
5. Ekranı kilitleyip aynı testi yap. Realme 11/Android 15'te arka plan pil politikası ve mikrofon erişimi cihazda doğrulanmalıdır. Uygulama kullanıcı başlatmalı foreground mikrofon servisi kullanır; kapatılır veya yeniden başlatılırsa Android sürekli dinlemeyi garanti etmez.

## Tanılama

Ekrandaki **Tanılama** ve **Mikrofon kaydı** satırlarını ayrı ayrı oku. `adb logcat -s Jarvis OpenWakeWord` model/AudioRecord hatalarını gösterir. openWakeWord Android kütüphanesi asenkron başlar ve mikrofon ya da model hatasını uygulamaya geri çağırmaz; bu nedenle servis açık görünmesi algılamanın çalıştığını kanıtlamaz. Uygulama ses kaydı dosyası saklamaz.

Gemini Live'a yalnızca görüşme sırasında mikrofon sesi gönderilir. Giriş 16 kHz PCM, yanıt 24 kHz PCM'dir. [Google'ın Live API belgeleri](https://ai.google.dev/gemini-api/docs/live-api) ve [fiyatlandırması](https://ai.google.dev/gemini-api/docs/pricing) geçerlidir; ücretsiz katman sınırlıdır. Ses oturumu veya model kullanılamıyorsa bağlantı hatası Tanılama alanında görünür. Resmî belgeler mobil istemciler için kısa ömürlü anahtar ve sunucu önerir; bilgisayarsız bu kişisel denemede kullanıcı anahtarı cihazda saklanır.

## Kaynaklar

Yerel `jarvis_v1.onnx` modeli [fwartner wakeword koleksiyonundan](https://github.com/fwartner/home-assistant-wakewords-collection/tree/main/en/jarvis) CI sırasında alınır. openWakeWord Android kodu Apache-2.0, yerleşik ön işleme modellerinin lisansı CC BY-NC-SA 4.0'dır. Yanlış veya kaçırılmış tetiklemeler gerçek telefonda ölçülmelidir.

