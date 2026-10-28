#!/usr/bin/env bash
# Kullanım:
#   ./tv-temizlik.sh            → listedekileri devre dışı bırakır (önce yedek alır)
#   ./tv-temizlik.sh geri       → yedekteki her şeyi geri açar
# TV adresi: TV=192.168.1.86 ./tv-temizlik.sh
set -u
TV="${TV:-192.168.1.86}"
DIZIN="$(cd "$(dirname "$0")" && pwd)"
LISTE="$DIZIN/tv-temizlik.txt"
YEDEK="$DIZIN/tv-temizlik-yedek.txt"
KORUMALI='^(com\.amazon\.amazonvideo\.livingroom|com\.beonetv\.tod|com\.google\.android\.youtube\.tv|com\.siam\.tvasistan|com\.google\.android\.tvlauncher|com\.google\.android\.katniss|com\.google\.android\.apps\.mediashell|com\.mediatek\..*tv.*|com\.mediatek\.tvinput|com\.vestel\.tv\.(service|keycontroller|backlight|eib)|com\.google\.android\.tungsten\.setupwraith|com\.android\.(vending|tv\.settings|providers\..*|systemui)|com\.google\.android\.(gms|gsf))$'

adb connect "$TV:5555" >/dev/null 2>&1
adb -s "$TV:5555" get-state >/dev/null 2>&1 || { echo "TV'ye bağlanılamadı ($TV). TV açık ve ADB izni verilmiş olmalı."; exit 1; }
A() { adb -s "$TV:5555" shell "$@"; }

if [ "${1:-}" = "geri" ]; then
  [ -f "$YEDEK" ] || { echo "Yedek yok: $YEDEK"; exit 1; }
  while read -r p; do [ -n "$p" ] && A pm enable --user 0 "$p" >/dev/null 2>&1 && echo "açıldı: $p"; done < "$YEDEK"
  echo "Bitti. TV'yi yeniden başlatın: adb -s $TV:5555 reboot"; exit 0
fi

echo "ÖNCE bellek:"; A "cat /proc/meminfo | grep -E 'MemTotal|MemAvailable'"
KURULU="$(A pm list packages -e | tr -d '\r' | sed 's/^package://')"
: > "$YEDEK.yeni"
grep -v '^\s*#' "$LISTE" | sed 's/#.*//' | tr -d ' \r' | grep -v '^$' | while read -r p; do
  if echo "$p" | grep -Eq "$KORUMALI"; then echo "KORUMALI, atlandı: $p"; continue; fi
  if ! echo "$KURULU" | grep -qx "$p"; then echo "yok/zaten kapalı: $p"; continue; fi
  if A pm disable-user --user 0 "$p" 2>&1 | grep -q "new state: disabled"; then
    echo "kapatıldı: $p"; echo "$p" >> "$YEDEK.yeni"
  else
    echo "kapatılamadı: $p"
  fi
done
[ -f "$YEDEK" ] && cat "$YEDEK" >> "$YEDEK.yeni"; sort -u "$YEDEK.yeni" > "$YEDEK"; rm -f "$YEDEK.yeni"
echo; echo "Yedek: $YEDEK  (geri almak için: ./tv-temizlik.sh geri)"
echo "Şimdi TV'yi yeniden başlatın: adb -s $TV:5555 reboot"
echo "Açılınca kontrol: ana ekran, HDMI 1-3, canlı TV, kumanda tuşları, Prime/TOD/YouTube, Jülide."
echo "Bellek ölçümü için: adb -s $TV:5555 shell \"grep MemAvailable /proc/meminfo\""
