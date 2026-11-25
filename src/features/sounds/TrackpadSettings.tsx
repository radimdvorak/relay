import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Hand, Play } from "lucide-react";
import { useEffect, useState } from "react";
import { api } from "../../lib/api";
import { soundEventLabels, type SoundEvent } from "../../../shared/sounds";
import {
  defaultTapStrength,
  tapOptions,
  type TapId,
  type TrackpadSettings,
} from "../../../shared/trackpad";
import { SettingsSelect } from "../../ui/SettingsCard";
import { Slider } from "../../ui/Slider";
import { ErrorBox } from "../../ui/ui";
import { trackpadSettingsQuery } from "./state";
import "./sounds.css";

/** Saved as they change; a save on its way shows at once. */
function useTrackpadSettings() {
  const qc = useQueryClient();
  const saved = useQuery(trackpadSettingsQuery);
  const saving = useMutation({
    mutationFn: (settings: TrackpadSettings) =>
      api.saveTrackpadSettings(settings),
    onSuccess: (settings) =>
      qc.setQueryData(trackpadSettingsQuery.queryKey, settings),
  });
  return {
    value: saving.isPending ? saving.variables : saved.data,
    error: saved.error ?? saving.error,
    set: saving.mutate,
  };
}

const tap = (id: TapId, strength: number) =>
  void api.tapTrackpad(id, strength).catch(() => {});

/** What the trackpad does for one event; picking a pattern plays it. */
export function TrackpadEventSetting({ event }: { event: SoundEvent }) {
  const settings = useTrackpadSettings();
  const value = settings.value?.[event];
  const strength = settings.value?.strength ?? defaultTapStrength;
  return (
    <div className="sound-setting">
      <SettingsSelect
        label={`Trackpad: ${soundEventLabels[event]}`}
        value={value ?? "off"}
        options={[{ value: "off", label: "Nothing" }, ...tapOptions(event)]}
        onChange={(next) => {
          const picked = next === "off" ? undefined : (next as TapId);
          settings.set({ ...settings.value, [event]: picked });
          if (picked) tap(picked, strength);
        }}
      />
      <button
        type="button"
        className="sound-try"
        disabled={!value}
        aria-label="Feel it"
        title="Feel it"
        onClick={() => value && tap(value, strength)}
      >
        <Play size={13} />
      </button>
      {settings.error && <ErrorBox error={settings.error} />}
    </div>
  );
}

/** One strength for every pattern; letting go knocks at the new one. */
export function TrackpadStrengthSetting() {
  const settings = useTrackpadSettings();
  const saved = settings.value?.strength ?? defaultTapStrength;
  const [strength, setStrength] = useState(saved);
  useEffect(() => setStrength(saved), [saved]);
  const commit = (strength: number) => {
    if (strength === saved) return;
    settings.set({ ...settings.value, strength });
    tap(settings.value?.waiting ?? "knock-knock", strength);
  };
  return (
    <div className="sound-volume">
      <Slider
        min={0.05}
        max={1}
        step={0.05}
        defaultValue={defaultTapStrength}
        icon={<Hand />}
        value={strength}
        aria-label="Trackpad strength"
        format={(v) => `${Math.round(v * 100)}%`}
        onChange={setStrength}
        onCommit={commit}
      />
      <span className="settings-row-value">{Math.round(strength * 100)}%</span>
    </div>
  );
}
