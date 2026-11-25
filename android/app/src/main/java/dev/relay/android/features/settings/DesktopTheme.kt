package dev.relay.android.features.settings

import dev.relay.android.remote.model.PhoneAppearance
import dev.relay.android.remote.model.PhonePalette
import dev.relay.android.remote.model.RemoteJson
import dev.relay.android.ui.theme.DesktopAppearance
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The desktop's appearance as the theme takes it. */
internal fun PhoneAppearance.toDesktop() = DesktopAppearance(mode, light.toMap(), dark.toMap())

private fun PhonePalette.toMap(): Map<String, String> =
    RemoteJson.encodeToJsonElement(PhonePalette.serializer(), this).jsonObject.mapValues { it.value.jsonPrimitive.content }
