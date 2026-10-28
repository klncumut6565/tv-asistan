@echo off
chcp 65001 >nul
rem Windows: tv-temizlik.bat   |   geri almak: tv-temizlik.bat geri
set TV=192.168.1.86
set DIZIN=%~dp0
adb connect %TV%:5555 >nul
if /I "%1"=="geri" goto GERI
echo ONCE bellek:
adb -s %TV%:5555 shell "grep -E 'MemTotal|MemAvailable' /proc/meminfo"
for /f "usebackq eol=# tokens=1" %%p in ("%DIZIN%tv-temizlik.txt") do (
  echo %%p
  adb -s %TV%:5555 shell pm disable-user --user 0 %%p
  echo %%p>>"%DIZIN%tv-temizlik-yedek.txt"
)
echo.
echo Bitti. Simdi: adb -s %TV%:5555 reboot
goto SON
:GERI
for /f "usebackq tokens=1" %%p in ("%DIZIN%tv-temizlik-yedek.txt") do adb -s %TV%:5555 shell pm enable --user 0 %%p
echo Geri alindi. adb -s %TV%:5555 reboot
:SON
