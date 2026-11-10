# JARVIS Android 0.2.1

Bu bir Android uygulaması prototipidir. Telefon ekranı kilitliyken çalışması cihazın mikrofon / pil yönetimi kısıtlamalarına bağlıdır.

**İşlevler:** Kullanıcı uygulamayı açıp başlattıktan sonra cihaz üzerinde "Hey Jarvis" algılama, Android TTS ile "Buyurun efendim" yanıtı ve ChatGPT uygulamasını elle açmak için dokunulabilir bildirim.

**Sınırlar:** ChatGPT sesli sohbeti otomatik başlamaz; kilit ekranı aşılamaz. Bir Siri muadili değildir. Mikrofonun sürekli çalışması batarya tüketebilir. Açık kaynak seslenme modeli İngilizce "Hey Jarvis" telaffuzunu bekler. Demo henüz gerçek Android cihazda doğrulanmadı.

## Kurulum
GitHub -> Actions -> JARVIS Android APK -> son başarılı çalıştırma -> Artifacts -> JARVIS-Android-APK. İndirilen ZIP içindeki `JARVIS-Hey-Jarvis-debug.apk` dosyasını Android telefonuna yükle. Uygulamayı aç, mikrofon ve bildirim izinlerini ver, "Dinlemeyi başlat" düğmesine bas ve sonra ekranı kilitle. Türkçe ses paketi kurulu olmalı. Arka planda çalışması için cihazın pil ayarlarında uygulamanın kısıtlanmaması gerekebilir.

**Hata halinde** Actions günlüklerini kontrol et. Debug APK geliştirme/test amaçlıdır, mağaza yayınına uygun değildir.

openWakeWord Android: https://github.com/msnilsen/openwakeword-android (Apache-2.0); hazır seslenme modelleri CC BY-NC-SA 4.0, ticari kullanıma uygun değildir.
