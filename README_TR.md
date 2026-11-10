# JARVIS Android 0.6.0

Telefonda tek başına çalışan JARVIS denemesi. Ekrandaki **DİNLEMEYİ BAŞLAT** ile mikrofon ön plan hizmeti açılır. Yerel model tek kelimelik “Jarvis” seslenişini algılarsa mikrofonu Gemini Live görüşmesine bırakır. ChatGPT uygulamasını veya güç düğmesini otomatik çalıştırmaz.

## Kurulum ve sıralı test

1. [Releases](https://github.com/ferzan667/jarvis-android/releases) sayfasından `JARVIS-debug.apk` dosyasını indir. Önceki debug APK farklı imzayla kurulmuşsa kaldırıp yeniden kurman gerekebilir.
2. Google AI Studio'da oluşturduğun Gemini API anahtarını yalnızca telefon uygulamasına gir ve **ANAHTARI KAYDET** düğmesine bas. Sohbete veya GitHub'a gönderme.
3. Mikrofon iznini ver ve **GEMINI SESLİ GÖRÜŞMEYİ DENE** düğmesiyle konuşmayı ayrı test et. Tanılama alanında bağlantı hatası varsa önce bunu çöz.
4. Görüşmeyi bitirip **DİNLEMEYİ BAŞLAT** düğmesine bas. **ALGILAYICI / CAPTURING** ve sesinle değişen **MİKROFON** yüzdesini görmeden “Jarvis” testine geçme. **JARVIS SKORU** 0.35 eşiğini geçerse görüşme açılır.
5. Ekran kapalı testini sonra yap. Realme pil ayarlarında JARVIS için arka plan çalışmasına izin ver. Android veya Realme hizmeti yine de sonlandırabilir; cihazda doğrulanmalıdır.

## Tanılama

**MODEL_LOADING** uzun süre kalırsa model yüklenmemiş olabilir. **MIC_ERROR** mikrofonun açılamadığını gösterir. **INFERENCE_ERROR** model işleminde hata olduğunu gösterir. Mikrofon yüzdesi sıfırda kalırsa izin, başka ses kaydı veya Android mikrofon ayarlarını kontrol et. `adb logcat -s Jarvis OpenWakeWord` ayrıntılı hatayı gösterir. Bu sürümde model ve mikrofon durumları asenkron algılayıcıdan ekrana aktarılır; “servis açık” tek başına başarı sayılmaz.

Gemini Live ses akışı için internet ve desteklenen API anahtarı gerekir. Ses kayıt dosyası saklanmaz. Anahtar cihazda Android Keystore ile şifrelenir. [Google Live API belgeleri](https://ai.google.dev/gemini-api/docs/live-api) ve [fiyatlandırması](https://ai.google.dev/gemini-api/docs/pricing) geçerlidir.

## Kaynak ve lisans

Tek kelimelik `jarvis_v1.onnx` modeli [fwartner koleksiyonundan](https://github.com/fwartner/home-assistant-wakewords-collection/tree/main/en/jarvis), openWakeWord Android kodu [msnilsen/openwakeword-android](https://github.com/msnilsen/openwakeword-android) sürüm 0.1.2'den alınır. Kod Apache-2.0; ön işleme modelleri CC BY-NC-SA 4.0 koşullarına tabidir. Kaynak lisans `THIRD_PARTY_LICENSES` klasöründedir. Fiziksel cihazda sesleniş doğruluğu henüz doğrulanmadı.

