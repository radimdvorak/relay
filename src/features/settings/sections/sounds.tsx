import { useQuery } from "@tanstack/react-query";
import type { SettingEntry } from "../settings-search";
import {
  soundEventHints,
  soundEventLabels,
  soundEvents,
} from "../../../../shared/sounds";
import {
  CustomSoundsSetting,
  SoundEventSetting,
  SoundVolumeSetting,
} from "../../sounds/SoundSettings";

import { trackpadAvailableQuery } from "../../sounds/state";
import {
  TrackpadEventSetting,
  TrackpadStrengthSetting,
} from "../../sounds/TrackpadSettings";

const keywords = "sound sounds audio chime ping notification alert beep play";

export function useSoundEntries(): SettingEntry[] {
  const trackpad = useQuery(trackpadAvailableQuery).data;
  if (!trackpad) return soundEntries();
  return [...soundEntries(), ...trackpadEntries()];
}

function trackpadEntries(): SettingEntry[] {
  const keywords =
    "trackpad haptic haptics vibrate vibration knock tap force touch";
  return [
    ...soundEvents.map((event): SettingEntry => ({
      id: `trackpad-${event}`,
      category: "sounds",
      section: "Trackpad",
      title: soundEventLabels[event],
      description: soundEventHints[event],
      keywords,
      render: () => <TrackpadEventSetting event={event} />,
    })),
    {
      id: "trackpad-strength",
      category: "sounds",
      section: "Trackpad",
      title: "Strength",
      description:
        "How hard the trackpad moves. A project that keeps an event's sound off keeps the trackpad still too.",
      keywords,
      render: () => <TrackpadStrengthSetting />,
    },
  ];
}

function soundEntries(): SettingEntry[] {
  return [
    {
      id: "sound-finished",
      category: "sounds",
      title: "Done",
      description: soundEventHints.finished,
      keywords: `${keywords} done finished complete turn`,
      render: () => <SoundEventSetting event="finished" />,
    },
    {
      id: "sound-waiting",
      category: "sounds",
      title: "Needs you",
      description: soundEventHints.waiting,
      keywords: `${keywords} needs you waiting question approval plan input`,
      render: () => <SoundEventSetting event="waiting" />,
    },
    {
      id: "sound-failed",
      category: "sounds",
      title: "Failed or stopped",
      description: soundEventHints.failed,
      keywords: `${keywords} failed error limit stopped`,
      render: () => <SoundEventSetting event="failed" />,
    },
    {
      id: "sound-volume",
      category: "sounds",
      title: "Volume",
      keywords: `${keywords} volume loud quiet level`,
      render: () => <SoundVolumeSetting />,
    },
    {
      id: "sound-custom",
      category: "sounds",
      section: "Your sounds",
      title: "Sound files",
      description:
        "Add an MP3, WAV, OGG, M4A, AAC or FLAC file of up to 10 seconds; it shows up beside the built-in sounds for every event.",
      keywords: `${keywords} custom upload file mp3 wav own add`,
      block: true,
      render: () => <CustomSoundsSetting />,
    },
  ];
}
