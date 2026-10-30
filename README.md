# Orbic Recorder v3.1.1 — correct-source package

Nguyên nhân giao diện cu: `MainActivity.java` / `RecorderService.java` v3.1 bi dat o thu muc goc, trong khi Gradle chi bien dich `app/src/main/java/com/example/orbicrecorder/` (dang la v3.0). File `build.yml` o goc cung khong duoc GitHub Actions su dung.

## Upload len repository (quan trong)

1. Nên sao luu repo / tao branch truoc khi thay doi.
2. Ghi de cac file trong goi ZIP nay vao dung **duong dan** trong repository. Khong dat nguyen thu muc ZIP thanh mot thu muc con trong repo.
3. **XOA** 3 file nam o goc repository (neu co): `MainActivity.java`, `RecorderService.java`, `build.yml`. Chúng không được Gradle/GitHub Actions sử dụng nhưng rất dễ gây nhầm lẫn.
4. Kiem tra `app/src/main/java/com/example/orbicrecorder/MainActivity.java` co dong `Voice Filter v3.1.1`.
5. Kiem tra `app/build.gradle` la `versionCode 5`, `versionName '3.1.1-voice-filter'`.
6. Kiem tra `.github/workflows/build.yml` co ten `Build Orbic Recorder v3.1.1 APK`.
7. Commit va chay workflow `Build Orbic Recorder v3.1.1 APK`. Workflow se BAO LOI RO RANG neu van con Java o goc hoac giao dien cu.
8. Tai artifact `orbic-recorder-v3.1.1-apk` tu lan build moi nhat. Tren man hinh phai hien `Voice Filter v3.1.1 • WAV 16 kHz`.

## Quan trong

- Day la ma nguon, chua duoc bien dich/kiem thu APK trong moi truong nay.
- APK debug co the khac chu ky voi phien ban cu: neu Android khong cho cai de, sao luu WAV truoc khi go cai dat.
- `versionCode 5` giup phan biet ban moi nhung KHONG giai quyet sai khoa ky APK.
- APK nay van la app Java thuan, **chua co OTA/PiP**.
