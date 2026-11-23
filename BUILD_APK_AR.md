# بناء APK فـ Android Studio

1. نزّل وثبّت Android Studio (مجاني) من developer.android.com/studio
2. فك ملف ZIP فدوسي بلا حروف عربية ولا فراغات فالمسار (مثلاً `C:\Projets\TopoCalcBTP`).
3. Android Studio ← File ← Open ← اختار الدوسي `TopoCalcBTP` ← Trust Project.
4. تسنى حتى يكمل "Gradle sync" (كيحتاج الأنترنيت، المرة الأولى 5–20 دقيقة).
   إلا طلب منك يثبت Android SDK 35 ولا شي حاجة ناقصة: قبل (Install).
5. Build ← Build App Bundle(s) / APK(s) ← Build APK(s).
6. ملي يسالي، اضغط "locate" ولا سير لـ:
   `app/build/outputs/apk/debug/app-debug.apk`
7. نقل الملف للتيليفون (كابل، WhatsApp، Drive…) وحلو. سمح بـ «تثبيت تطبيقات غير معروفة» إلا طلبها.

## إلا طلع خطأ
- فخانة "Build" التحتانية، انسخ أول سطر أحمر فيه `e:` ولا `error:` مع اسم الملف ورقم السطر، وصيفطو.
- ولا صوّر الشاشة.

## تجربة المحرك بوحدو (اختياري)
فـ Terminal ديال Android Studio:
`gradlew :topo-engine:verifyEngine`  ← خاص يطلع `41 test groups passed`.
