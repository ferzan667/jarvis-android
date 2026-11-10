# JARVIS Android 0.7.3

Telefonda tek başına çalışan JARVIS denemesi. Uygulama açılınca izinleri alır ve mikrofon ön plan hizmetini kendiliğinden başlatır. Yerel model tek kelimelik “Jarvis” seslenişini algılarsa mikrofonu Gemini Live görüşmesine bırakır. ChatGPT uygulamasını veya güç düğmesini otomatik çalıştırmaz.

## Kurulum ve sıralı test

1. [Releases](https://github.com/ferzan667/jarvis-android/releases) sayfasından `JARVIS-debug.apk` dosyasını indir. Önceki debug APK farklı imzayla kurulmuşsa kaldırıp yeniden kurman gerekebilir.
2. Google AI Studio'da oluşturduğun Gemini API anahtarını yalnızca telefon uygulamasına gir ve **ANAHTARI KAYDET** düğmesine bas. Sohbete veya GitHub'a gönderme.
3. Uygulamayı açıp mikrofon ve bildirim izinlerini ver. **ALGILAYICI / CAPTURING** ve sesinle değişen **MİKROFON** yüzdesini kontrol et. **JARVIS SKORU** 0.25 eşiğini geçerse Gemini görüşmesi kendiliğinden bağlanır.
4. Bağlantı sağlanırsa sesli bir “Buyurun efendim” yanıtı istenir. Gemini bağlantı hatası varsa **TANILAMA** alanındaki metni paylaş.
5. Ekran kapalı testini sonra yap. Realme pil ayarlarında JARVIS için arka plan çalışmasına izin ver. Android veya Realme hizmeti yine de sonlandırabilir; cihazda doğrulanmalıdır.

## Tanılama

**MODEL_LOADING** uzun süre kalırsa model yüklenmemiş olabilir. **MIC_ERROR** mikrofonun açılamadığını gösterir. **INFERENCE_ERROR** model işleminde hata olduğunu gösterir. Mikrofon yüzdesi sıfırda kalırsa izin, başka ses kaydı veya Android mikrofon ayarlarını kontrol et. `adb logcat -s Jarvis OpenWakeWord` ayrıntılı hatayı gösterir. Bu sürümde model ve mikrofon durumları asenkron algılayıcıdan ekrana aktarılır; “servis açık” tek başına başarı sayılmaz.

Gemini bağlanırken algılayıcı mikrofonu serbest bırakır; bu sırada mikrofon seviyesi ve Jarvis skoru gösterilmez. “Gemini görüşmesi açık” yalnızca bağlantı ve ses donanımı hazır olduktan sonra görünür. Bağlantı 20 saniyede kurulmazsa hata gösterilir ve JARVIS dinlemeye geri döner.

Sesli karşılama sırasında Gemini'ye mikrofon sesi gönderilmez. Karşılama yanıtı bittikten sonra görüşme mikrofonu açılır; yanıt gelmezse 8 saniye sonra görüşme mikrofonu yine açılır. Tanılama alanı karşılama, ses çıkışı ve mikrofon akışı aşamalarını ayrı ayrı bildirir.

Tanılama artık son “Jarvis” tetiklemesinin saatini ve skorunu, son Gemini bağlantı sonucunu ve bu dinleme boyunca görülen en yüksek model skorunu ayrı saklar. Yeniden dinleme başlasa bile Gemini hata mesajı kaybolmaz.

Gemini Live ses akışı için internet ve desteklenen API anahtarı gerekir. Ses kayıt dosyası saklanmaz. Anahtar cihazda Android Keystore ile şifrelenir. [Google Live API belgeleri](https://ai.google.dev/gemini-api/docs/live-api) ve [fiyatlandırması](https://ai.google.dev/gemini-api/docs/pricing) geçerlidir.

## Kaynak ve lisans

Tek kelimelik `jarvis_v1.onnx` modeli [fwartner koleksiyonundan](https://github.com/fwartner/home-assistant-wakewords-collection/tree/main/en/jarvis), openWakeWord Android kodu [msnilsen/openwakeword-android](https://github.com/msnilsen/openwakeword-android) sürüm 0.1.2'den alınır. Kod Apache-2.0; ön işleme modelleri CC BY-NC-SA 4.0 koşullarına tabidir. Kaynak lisans `THIRD_PARTY_LICENSES` klasöründedir. Fiziksel cihazda sesleniş doğruluğu henüz doğrulanmadı.

