using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;
using Windows.Media.Control;
using Windows.Storage.Streams;

namespace SMTCBridge
{
    // COM Interfaces for Audio Control
    [Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioEndpointVolume
    {
        int RegisterControlChangeNotify(IntPtr notify);
        int UnregisterControlChangeNotify(IntPtr notify);
        int GetChannelCount(out int channelCount);
        int SetMasterVolumeLevel(float levelDB, ref Guid eventContext);
        int SetMasterVolumeLevelScalar(float level, ref Guid eventContext);
        int GetMasterVolumeLevel(out float levelDB);
        int GetMasterVolumeLevelScalar(out float level);
        int SetChannelVolumeLevel(uint channel, float levelDB, ref Guid eventContext);
        int SetChannelVolumeLevelScalar(uint channel, float level, ref Guid eventContext);
        int GetChannelVolumeLevel(uint channel, out float levelDB);
        int GetChannelVolumeLevelScalar(uint channel, out float level);
        int SetMute(bool mute, ref Guid eventContext);
        int GetMute(out bool mute);
        int GetVolumeStepInfo(out uint step, out uint steps);
        int VolumeStepUp(ref Guid eventContext);
        int VolumeStepDown(ref Guid eventContext);
        int QueryHardwareSupport(out uint hardwareSupportMask);
        int GetVolumeRange(out float volumeMin, out float volumeMax, out float volumeStep);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDevice
    {
        int Activate(ref Guid id, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object interfacePointer);
        int OpenPropertyStore(int stgmAccess, out IntPtr properties);
        int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        int GetState(out int state);
    }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
        int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
        int RegisterEndpointNotificationCallback(IntPtr client);
        int UnregisterEndpointNotificationCallback(IntPtr client);
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    internal class MMDeviceEnumerator
    {
    }

    class Program
    {
        private static IAudioEndpointVolume _volumeControl;
        private static GlobalSystemMediaTransportControlsSessionManager _sessionManager;
        private static string _artworkSavePath;
        private static string _lastTrackId = "";
        private static readonly object _consoleLock = new object();
        // Position self-tracking (SMTC LastUpdatedTime is unreliable)
        private static double _lastRawPosition = -1;
        private static DateTime _positionReferenceTime = DateTime.UtcNow;
        private static double _positionReferenceValue = 0;

        static void Main(string[] args)
        {
            try
            {
                Console.OutputEncoding = System.Text.Encoding.UTF8;
                Console.InputEncoding = System.Text.Encoding.UTF8;
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("Failed to set console encoding: " + ex.Message);
            }

            // Parse arguments: output artwork path
            _artworkSavePath = args.Length > 0 ? args[0] : Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "windows-media-art.png");

            try
            {
                var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumerator();
                IMMDevice device;
                if (enumerator.GetDefaultAudioEndpoint(0, 0, out device) == 0)
                {
                    Guid iidVolume = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A");
                    object volumeObj;
                    if (device.Activate(ref iidVolume, 1, IntPtr.Zero, out volumeObj) == 0)
                    {
                        _volumeControl = (IAudioEndpointVolume)volumeObj;
                    }
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("Volume init error: " + ex.Message);
            }

            try
            {
                _sessionManager = GlobalSystemMediaTransportControlsSessionManager.RequestAsync().GetAwaiter().GetResult();
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("SMTC init error: " + ex.Message);
            }

            // Start stdin reading thread
            Thread inputThread = new Thread(ReadInputLoop);
            inputThread.IsBackground = true;
            inputThread.Start();

            // Main polling/heartbeat loop
            while (true)
            {
                UpdateStatus();
                Thread.Sleep(200);
            }
        }

        private static void UpdateStatus()
        {
            if (_sessionManager == null) return;

            try
            {
                GlobalSystemMediaTransportControlsSession session = null;
                try
                {
                    var sessions = _sessionManager.GetSessions();
                    if (sessions != null)
                    {
                        foreach (var s in sessions)
                        {
                            var sInfo = s.GetPlaybackInfo();
                            if (sInfo != null && sInfo.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing)
                            {
                                session = s;
                                break;
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine("Error enumerating sessions: " + ex.Message);
                }

                if (session == null)
                {
                    session = _sessionManager.GetCurrentSession();
                }

                if (session == null)
                {
                    WriteJson(new
                    {
                        Title = "",
                        Artist = "",
                        IsPlaying = false,
                        PositionSeconds = 0.0,
                        DurationSeconds = 0.0,
                        VolumePercent = GetVolume(),
                        ArtworkPath = ""
                    });
                    return;
                }

                var props = session.TryGetMediaPropertiesAsync().GetAwaiter().GetResult();
                var info = session.GetPlaybackInfo();
                var timeline = session.GetTimelineProperties();

                bool isPlaying = info != null && info.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
                string title = props != null ? props.Title : "";
                string artist = props != null ? props.Artist : "";
                double duration = timeline != null ? timeline.EndTime.TotalSeconds : 0.0;
                double rawPosition = timeline != null ? timeline.Position.TotalSeconds : 0.0;

                // Self-track position: when SMTC reports a new position, record it as reference
                // Then calculate real-time position from our own clock
                double position;
                if (Math.Abs(rawPosition - _lastRawPosition) > 0.5)
                {
                    // SMTC position changed (seek, new track, unpause) — reset reference
                    _lastRawPosition = rawPosition;
                    _positionReferenceValue = rawPosition;
                    _positionReferenceTime = DateTime.UtcNow;
                    position = rawPosition;
                }
                else if (isPlaying)
                {
                    // Position unchanged but playing — calculate from our reference
                    double elapsed = (DateTime.UtcNow - _positionReferenceTime).TotalSeconds;
                    position = _positionReferenceValue + elapsed;
                    if (duration > 0 && position > duration) position = duration;
                }
                else
                {
                    // Paused — use raw position, reset reference
                    _lastRawPosition = rawPosition;
                    _positionReferenceValue = rawPosition;
                    _positionReferenceTime = DateTime.UtcNow;
                    position = rawPosition;
                }

                int volume = GetVolume();

                string trackId = title + "|" + artist;
                string finalArtworkPath = "";

                if (trackId != _lastTrackId)
                {
                    try
                    {
                        if (File.Exists(_artworkSavePath))
                        {
                            File.Delete(_artworkSavePath);
                        }
                    }
                    catch { }
                }

                if (props != null && props.Thumbnail != null)
                {
                    try
                    {
                        var stream = props.Thumbnail.OpenReadAsync().GetAwaiter().GetResult();
                        ulong streamSize = stream.Size;
                        long fileLength = 0;
                        if (File.Exists(_artworkSavePath))
                        {
                            fileLength = new FileInfo(_artworkSavePath).Length;
                        }

                        // If the track changed, or the file doesn't exist, or the stream size is different, write/update the artwork
                        if (trackId != _lastTrackId || fileLength == 0 || (long)streamSize != fileLength)
                        {
                            string tempPath = _artworkSavePath + ".tmp";
                            try
                            {
                                using (var netStream = stream.AsStreamForRead())
                                using (var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write))
                                {
                                    netStream.CopyTo(fileStream);
                                }

                                // Filter out browser icons (Chrome/Edge send their icon as thumbnail)
                                // Real album art is typically > 10KB, browser icons are tiny
                                var tempInfo = new FileInfo(tempPath);
                                if (tempInfo.Length < 10000)
                                {
                                    // Too small — likely a browser icon, not real artwork
                                    try { File.Delete(tempPath); } catch { }
                                    try { if (File.Exists(_artworkSavePath)) File.Delete(_artworkSavePath); } catch { }
                                    finalArtworkPath = "";
                                }
                                else
                                {
                                    // Real artwork — atomic move
                                    if (File.Exists(_artworkSavePath))
                                    {
                                        File.Delete(_artworkSavePath);
                                    }
                                    File.Move(tempPath, _artworkSavePath);
                                    finalArtworkPath = _artworkSavePath;
                                }
                            }
                            catch (Exception ex)
                            {
                                Console.Error.WriteLine("Art save error: " + ex.Message);
                                // Cleanup temp
                                try { if (File.Exists(tempPath)) File.Delete(tempPath); } catch { }
                                finalArtworkPath = File.Exists(_artworkSavePath) ? _artworkSavePath : "";
                            }
                        }
                        else
                        {
                            // Same track and same size — use existing artwork
                            finalArtworkPath = File.Exists(_artworkSavePath) ? _artworkSavePath : "";
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.Error.WriteLine("Error reading thumbnail stream: " + ex.Message);
                        finalArtworkPath = File.Exists(_artworkSavePath) ? _artworkSavePath : "";
                    }
                }

                _lastTrackId = trackId;

                WriteJson(new
                {
                    Title = title,
                    Artist = artist,
                    IsPlaying = isPlaying,
                    PositionSeconds = position,
                    DurationSeconds = duration,
                    VolumePercent = volume,
                    ArtworkPath = finalArtworkPath
                });
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine("Status update error: " + ex.Message);
            }
        }

        private static int GetVolume()
        {
            if (_volumeControl == null) return 100;
            try
            {
                float level;
                if (_volumeControl.GetMasterVolumeLevelScalar(out level) == 0)
                {
                    return (int)(level * 100);
                }
            }
            catch {}
            return 100;
        }

        private static void SetVolume(int percent)
        {
            if (_volumeControl == null) return;
            try
            {
                float val = Math.Max(0.0f, Math.Min(1.0f, percent / 100.0f));
                Guid guid = Guid.Empty;
                _volumeControl.SetMasterVolumeLevelScalar(val, ref guid);
            }
            catch {}
        }

        private static void WriteJson(object obj)
        {
            lock (_consoleLock)
            {
                // Simple JSON serializer to avoid referencing external libraries
                string json = SerializeJson(obj);
                Console.WriteLine(json);
            }
        }

        private static string SerializeJson(object obj)
        {
            var props = obj.GetType().GetProperties();
            System.Text.StringBuilder sb = new System.Text.StringBuilder();
            sb.Append("{");
            for (int i = 0; i < props.Length; i++)
            {
                var p = props[i];
                var name = p.Name;
                var val = p.GetValue(obj, null);
                sb.Append("\"").Append(name).Append("\":");
                if (val is string)
                {
                    sb.Append("\"").Append(EscapeString((string)val)).Append("\"");
                }
                else if (val is bool)
                {
                    sb.Append(((bool)val) ? "true" : "false");
                }
                else if (val is double)
                {
                    sb.Append(((double)val).ToString("F2", System.Globalization.CultureInfo.InvariantCulture));
                }
                else if (val is int)
                {
                    sb.Append((int)val);
                }
                else
                {
                    sb.Append("null");
                }
                if (i < props.Length - 1) sb.Append(",");
            }
            sb.Append("}");
            return sb.ToString();
        }

        private static string EscapeString(string s)
        {
            if (string.IsNullOrEmpty(s)) return "";
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }

        private static void ReadInputLoop()
        {
            string line;
            while ((line = Console.ReadLine()) != null)
            {
                line = line.Trim().ToLower();
                if (string.IsNullOrEmpty(line)) continue;

                try
                {
                    if (_sessionManager == null) continue;
                    var session = _sessionManager.GetCurrentSession();
                    if (session == null) continue;

                    if (line == "play" || line == "playpause" || line == "toggle")
                    {
                        session.TryTogglePlayPauseAsync();
                    }
                    else if (line == "pause")
                    {
                        session.TryPauseAsync();
                    }
                    else if (line == "next")
                    {
                        session.TrySkipNextAsync();
                    }
                    else if (line == "previous" || line == "prev")
                    {
                        session.TrySkipPreviousAsync();
                    }
                    else if (line.StartsWith("volume "))
                    {
                        int pct;
                        if (int.TryParse(line.Substring(7), out pct))
                        {
                            SetVolume(pct);
                        }
                    }
                }
                catch (Exception ex)
                {
                    Console.Error.WriteLine("Input cmd execution error: " + ex.Message);
                }
            }
        }
    }
}
