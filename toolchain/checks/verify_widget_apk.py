#!/usr/bin/env python3
"""Check a built LumaOS APK for native providers, metadata, resources and DEX classes."""
import argparse
import re
import struct
import subprocess
import zipfile

PACKAGE = 'com.lumaos.pro.launcher'
WIDGETS = {
    'widgets.builtin.DigitalClockWidgetProvider': ('digital', 'digital'),
    'widgets.builtin.AnalogClockWidgetProvider': ('analog', 'analog'),
    'widgets.builtin.CalendarWidgetProvider': ('calendar', 'reading'),
    'widgets.builtin.WeatherWidgetProvider': ('weather', 'reading'),
    'widgets.builtin.StorageWidgetProvider': ('storage', 'reading'),
    'widgets.builtin.ScreenTimeWidgetProvider': ('usage', 'reading'),
    'widgets.music.MusicWidgetProvider': ('music', 'music'),
    'widgets.battery.BatteryWidgetProvider': ('battery', 'battery'),
}


def dex_classes(data):
    def u32(at):
        return struct.unpack_from('<I', data, at)[0]

    string_ids, type_ids, count, defs = [u32(at) for at in (60, 68, 96, 100)]
    for index in range(count):
        descriptor_index = u32(type_ids + 4 * u32(defs + index * 32))
        start = u32(string_ids + 4 * descriptor_index)
        while data[start] & 128:  # UTF-16 size encoded as ULEB128
            start += 1
        start += 1
        if data[start] == ord('L'):
            yield data[start + 1:data.index(b';', start)].decode('utf-8', 'replace').replace('/', '.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('--aapt2', default='aapt2')
    args = parser.parse_args()

    def dump(kind, *options):
        return subprocess.check_output([args.aapt2, 'dump', kind, args.apk, *options], text=True)

    manifest = dump('xmltree', '--file', 'AndroidManifest.xml')
    resources = dump('resources')
    entries = re.split(r'(?m)^ +resource ', resources)[1:]
    ids = {}
    files = {}
    for entry in entries:
        value, name, *_ = entry.split()
        ids[name] = value
        file = re.search(r'\(file\) (\S+) type=', entry)
        if file:
            files[name] = file.group(1)
    receivers = re.split(r'(?m)^ +E: (?:receiver|activity|service|provider) .*$', manifest)
    with zipfile.ZipFile(args.apk) as apk:
        classes = {name for file in apk.namelist() if re.fullmatch(r'classes\d*\.dex', file)
                   for name in dex_classes(apk.read(file))}
        for name, (provider, layout) in WIDGETS.items():
            full = f'{PACKAGE}.{name}'
            assert full in classes, f'{full}: missing from DEX'
            node = next((node for node in receivers if re.search(
                rf'android:name\([^)]*\)="(?:{re.escape(full)}|\.{re.escape(name)})"', node)), '')
            provider_id = ids.get(f'xml/luma_{provider}_widget')
            assert node and 'android.appwidget.action.APPWIDGET_UPDATE' in node, f'{full}: missing receiver/update action'
            assert provider_id and 'android.appwidget.provider' in node and f'@{provider_id}' in node, f'{full}: missing provider metadata'
            provider_file = files.get(f'xml/luma_{provider}_widget')
            layout_id = ids.get(f'layout/widget_luma_{layout}')
            layout_file = files.get(f'layout/widget_luma_{layout}')
            assert provider_file and layout_id and layout_file, f'{full}: missing XML or native layout'
            provider_xml = dump('xmltree', '--file', provider_file)
            assert re.search(rf'android:initialLayout\([^)]*\)=@{layout_id}', provider_xml), f'{full}: missing native initialLayout'
            layout_xml = dump('xmltree', '--file', layout_file)
            expected = {'digital': 'TextClock', 'analog': 'AnalogClock'}.get(layout, 'TextView')
            assert f'E: {expected} ' in layout_xml, f'{full}: missing native {expected}'
            print(f'OK {full}: receiver, provider XML, RemoteViews layout and DEX')
        for name in ('widgets.builtin.WidgetSettingsActivity', 'widgets.builtin.WeatherRefreshJob'):
            assert f'{PACKAGE}.{name}' in classes and (name in manifest), f'{name}: missing component or DEX class'
        assert f'{PACKAGE}.widgets.WidgetHostController' in classes
    print('Verified 8 native providers, widget host, configuration activity and weather job.')


if __name__ == '__main__':
    main()
