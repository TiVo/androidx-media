import logging
import re
import select
import subprocess
import time
import datetime
from optparse import OptionParser
from enum import Enum, auto


def run_command(bash_cmd, returnOutput=False):
    logging.info(bash_cmd)
    process = subprocess.Popen(bash_cmd,
                               shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    process.poll()
    if process.returncode is None or process.returncode == 0:
        if returnOutput:
            out, err = process.communicate()
            return out
        else:
            return process
    else:
        out, err = process.communicate()
        raise Exception(err.decode(encoding='utf-8').strip('\n'))

# Live adjustment logging
liveAdjustStopped = re.compile('.*LoggingLivePlaybackSpeedControl.*Adjustment stopped with liveOffset ([0-9.]+).*')
liveAdjustOther = re.compile('.*LoggingLivePlaybackSpeedControl.*')

# CSV format Log from GeekStats with current live offset
# Now (time), Now (ms), Window Start, Window Start Time(ms), Position (time), Position (ms), Duration (ms), Live Offset (ms)
# 2023-07-20T11:51:49.437, 1689879109437, 2023-07-20T11:21:36.437, 1689877296000, 2023-07-20T11:51:16.414, 1689879076414, 1780414, 33.02
#  Group 2 - now in millis since epoch
#  Group 4 - Window start millis since epoch
#  Group 6 - Position in millis since spoch
#  Group 7 - Window duraiton
#  Group 8 - current live offset seconds
#
liveOffsetLog = re.compile('.*LivePosition: (.*?), (.*?), (.*?), (.*?), (.*?), (.*?), (.*?), (.*?)$')
otherLogInfo = re.compile('.*(ExoDemo.*(playUri|Version|Stopping)|AndroidRuntime)')

class Phase(Enum):
  INIT = auto()
  ADJUSTMENT = auto()
  MEASUREMENT = auto()
  DONE = auto()

class LivePosition:
    def __init__(self, streamer_info, live_offset_match):
        self.streamer_info = streamer_info
        self.live_offset_match = live_offset_match
        self.now = int(live_offset_match.group(2))
        self.position = int(live_offset_match.group(6))
        self.live_offset = float(live_offset_match.group(8))

    def getNowSeconds(self):
        return int(self.now / 1000)

    def printDeltas(self, live_position, sync_threshold):
        delta_now = self.now - live_position.now
        delta_pos = self.position - live_position.position
        delta_live = abs(abs(delta_pos) - abs(delta_now))
        if logging.getLogger().getEffectiveLevel() <= logging.WARNING:
            if delta_live > sync_threshold:
                self.logPositionDelta(delta_live, delta_now, delta_pos, live_position, sync_threshold)
        else:
            self.logPositionDelta(delta_live, delta_now, delta_pos, live_position, sync_threshold)

    def logPositionDelta(self, delta_live, delta_now, delta_pos, live_position, sync_threshold):
        level = logging.INFO if delta_live <= sync_threshold else logging.WARNING
        datetime_format = "%m-%d %H:%M:%S"
        now_seconds = float(live_position.now) / 1000.0
        dt = datetime.datetime.fromtimestamp(now_seconds)
        now_string = dt.strftime(datetime_format)

        logging.log(level, "%s - [%s] vs [%s]: live delta: %s (delta_now: %d delta_pos: %d)", now_string, live_position,
                        self, delta_live, delta_now, delta_pos)

    def __str__(self) -> str:
        datetime_format = "%H:%M:%S"
        pos_seconds = float(self.position) / 1000.0
        pos_milli = int(self.position) % 1000
        dt = datetime.datetime.fromtimestamp(pos_seconds)
        pos_string = dt.strftime(datetime_format)
        return "device: %s pos: %s.%s off: %s tar: %s" % (self.streamer_info.device_id, pos_string, pos_milli, self.streamer_info.last_live_offset, self.streamer_info.live_offset_target)


class StreamerInfo:
    def __init__(self, device_id, initial_adjusted, target_offset=None):
        self.device_id = device_id
        file_path = device_id.split(':')[0].replace('.', '_', ) + '_' + options.file_suffix
        self.out_file = open(file_path, "w")
        self.live_adjusted = initial_adjusted
        self.live_offset_target = target_offset
        self.last_live_offset = None
        self.live_offset_match = None

    def checkLiveAdjustLine(self, log_line):
        matched = False
        adjust_stopped_match = liveAdjustStopped.match(log_line)
        if adjust_stopped_match:
            if not self.live_adjusted:
                logging.info("device %s live offset ended, %s", self.device_id, log_line.rstrip())
            self.live_adjusted = True
            self.live_offset_target = adjust_stopped_match.group(1)
            matched = True
        elif liveAdjustOther.match(log_line):
            if self.live_adjusted:
                logging.info("device %s live offset started, %s", self.device_id, log_line.rstrip())
            self.live_adjusted = False
            matched = True
        return matched

    def checkLiveOffsetLine(self, log_line):
        live_position = None
        live_offset = liveOffsetLog.match(log_line)
        if live_offset:
            live_position = LivePosition(self, live_offset)
            self.last_live_offset = live_position.live_offset
            self.out_file.write(', '.join(live_offset.groups()[0:6]) + '\n')
        return live_position

    def startLogcat(self):
        for i in range(5):
            demoPid = run_command('adb -s %s shell pidof -s com.tivo.exoplayer.demo' % self.device_id, returnOutput=True)
            if demoPid:
                break
            else:
                time.sleep(3)
        if not demoPid:
          logging.error("ExoPlayer demo failed to start on %s", self.device_id)
          exit(1)
        self.logcat_proc = run_command("adb -s %s logcat --pid %s" % (self.device_id, demoPid.decode('utf-8').strip('\n')))

    def logcatLineIfReady(self):
        line = None
        if self.logcat_proc.stdout.readable():
            line = self.logcat_proc.stdout.readline().decode('utf-8')
            if otherLogInfo.match(line):
              logging.info("device: %s log_line: %s" % (self.device_id, line.rstrip()))
        return line

    def close(self):
        self.logcat_proc.terminate()
        self.out_file.close()

    def fileno(self):
        return self.logcat_proc.stdout.fileno()

    def __str__(self) -> str:
        if self.live_offset_target:
            return "device: %s off: %s tar: %s" % (self.device_id, self.last_live_offset, self.live_offset_target)
        else:
            return "device: %s no offset" % (self.device_id)


if __name__ == "__main__":
    parser = OptionParser(usage="Usage: %prog [options]")

    parser.add_option("-v", "--verbose",
                      action="store", dest="log_level", default="INFO",
                      help="Set logging verbosity, default %default ('CRITICAL', 'DEBUG', 'ERROR', 'FATAL', 'WARN' or 'INFO')")

    parser.add_option("-k", "--keep_running",
                      action="store_false", dest="stop_first", default="True",
                      help="Keep any current instance running")

    parser.add_option("-t", "--threshold",
                      type="float", dest="sync_threshold", default="30.0",
                      help="Milliseconds allowed to be out of sync, default %default (ms)")

    parser.add_option("-o", "--target_offset",
                      type=float, default="33.0",
                      help="Target offset in seconds")

    parser.add_option("-f", "--fast_resync",
                      type=float, default="20.0",
                      help="Enable fast sync mode, TV's will sync to live point quickly, sets percentage speed change")

    parser.add_option("", "--file_suffix",
                      action="store", dest="file_suffix", default="live_logging.log",
                      help="Set logfile base name")

    (options, args) = parser.parse_args()

    numeric_level = getattr(logging, options.log_level.upper(), None)
    logging.basicConfig(level=numeric_level)

    url = None
    if len(args) > 0:
        url = args[0]

    deviceListRaw = run_command("adb devices -l", returnOutput=True)
    deviceList = []
    for line in deviceListRaw.decode('utf-8').split('\n')[1:]:
        if line:
            deviceList.append(StreamerInfo(line.split(' ', 1)[0], url is None, target_offset=options.target_offset))

    for device in deviceList:
        run_command("adb -s %s logcat -c" % device.device_id, returnOutput=True)
        if url:
            if options.stop_first:
                run_command('adb -s %s shell am force-stop com.tivo.exoplayer.demo' % device.device_id, returnOutput=True)
            run_command('adb -s %s shell am start -n com.tivo.exoplayer.demo/.ViewActivity --ef live_offset %s --ef fast_resync %s  -a com.tivo.exoplayer.action.VIEW %s' %
                        (device.device_id, options.target_offset, options.fast_resync, url), returnOutput=True)
        device.startLogcat()

    last_live_offsets = {}
    current_phase = Phase.INIT

    while current_phase != Phase.DONE:
        try:
            all_devices_synced = all(device.live_adjusted for device in deviceList)
            current_phase = Phase.ADJUSTMENT if not all_devices_synced else Phase.MEASUREMENT

            logged_change = False
            while current_phase == Phase.ADJUSTMENT:
                if not logged_change:
                    logging.info("all devices are not synced to live position, entering Adjustment Phase")
                    logged_change = True

                readyOutDevices, _, _ = select.select(deviceList, [], [])
                for device in readyOutDevices:
                    log_line = device.logcatLineIfReady()
                    if log_line:
                        if device.checkLiveAdjustLine(log_line):
                            logging.info("adjust %s: %s" % (device.device_id, log_line.rstrip()))
                all_devices_synced = all(device.live_adjusted for device in deviceList)
                current_phase = Phase.ADJUSTMENT if not all_devices_synced else Phase.MEASUREMENT

            logging.info("all devices synced live position, entering Measurement Phase")

            while current_phase == Phase.MEASUREMENT:
                readyOutDevices, _, _ = select.select(deviceList, [], [])
                for device in readyOutDevices:
                    log_line = device.logcatLineIfReady()
                    if log_line:
                        if device.checkLiveAdjustLine(log_line):
                            logging.info("adjust %s: %s" % (device.device_id, log_line.rstrip()))

                        if device.live_adjusted:
                            live_offset =  device.checkLiveOffsetLine(log_line)
                            if live_offset:
                                now_seconds = live_offset.getNowSeconds()
                                live_offsets_at_time = last_live_offsets.get(now_seconds)
                                if live_offsets_at_time is None:
                                    live_offsets_at_time = [live_offset]
                                    last_live_offsets[now_seconds] = live_offsets_at_time
                                else:
                                    live_offsets_at_time.append(live_offset)
                                if len(live_offsets_at_time) == len(deviceList):
                                    # check the "delta between the deltas" matches,  that is the difference between position between
                                    # any pair of devices must match the difference between now() (sync'd to NTP)
                                    #
                                    for live_offset in live_offsets_at_time[1:]:
                                        live_offset.printDeltas(live_offsets_at_time[0], options.sync_threshold)
                                    del last_live_offsets[now_seconds]

                                logging.debug("device %s, now_seconds %s, live_position %s" % (device.device_id, now_seconds, log_line.rstrip()))
                        else:
                            logging.warning("device %s lost live sync, exiting Measurement Phase" % (device.device_id))
                            current_phase = Phase.ADJUSTMENT
                        break

        except KeyboardInterrupt:
            for device in deviceList:
                device.close()
            logging.info("Exiting on interrupt, playback should continue.")
            current_phase = Phase.DONE
            pass
