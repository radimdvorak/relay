import { api } from "../../lib/api";

export const soundSettingsQuery = {
  queryKey: ["sound-settings"],
  queryFn: () => api.soundSettings(),
  staleTime: Infinity,
};

export const trackpadSettingsQuery = {
  queryKey: ["trackpad-settings"],
  queryFn: () => api.trackpadSettings(),
  staleTime: Infinity,
};

/** Asked again each time Settings opens: a Magic Trackpad comes and goes. */
export const trackpadAvailableQuery = {
  queryKey: ["trackpad-available"],
  queryFn: () => api.trackpadAvailable(),
  staleTime: 0,
};

export const customSoundsQuery = {
  queryKey: ["custom-sounds"],
  queryFn: () => api.customSounds(),
  staleTime: Infinity,
};
