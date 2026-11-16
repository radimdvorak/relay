package dev.relay.android.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.relay.android.ui.theme.Radii
import dev.relay.android.ui.theme.RelayTheme

/**
 * A field for a secret that isn't a login: a token, a key, a pairing code.
 * A native password EditText, because that is what the platform's promises
 * hang on: TalkBack doesn't read what is typed, the keyboard is asked not to
 * learn it, and autofill is refused outright. Compose's own fields report
 * themselves to autofill, and a password manager then offers the app's saved
 * logins for a value that is no login.
 */
@Composable
fun SecretInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: () -> Unit = {},
) {
    val c = RelayTheme.colors
    val text = c.foreground.toArgb()
    val hint = c.faint.toArgb()
    val size = RelayTheme.type.base.fontSize.value
    Box(modifier.fillMaxWidth().border(1.dp, c.border, Radii.mdShape).padding(horizontal = 12.dp, vertical = 4.dp)) {
        AndroidView(
            factory = { context ->
                SecretEditText(context).apply {
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                        override fun afterTextChanged(s: Editable?) = changed(s?.toString().orEmpty())
                    })
                }
            },
            modifier = Modifier.fillMaxWidth(),
            update = { field ->
                field.changed = onChange
                field.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_DONE) onDone()
                    false
                }
                // Only what the field doesn't already hold: writing its own text back moves the cursor.
                if (field.text.toString() != value) field.setText(value)
                field.isEnabled = enabled
                field.hint = placeholder
                field.contentDescription = label
                field.setTextColor(text)
                field.setHintTextColor(hint)
                field.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            },
        )
    }
}

private class SecretEditText(context: Context) : EditText(context) {
    var changed: (String) -> Unit = {}

    init {
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        isSingleLine = true
        maxLines = 1
        background = ColorDrawable(android.graphics.Color.TRANSPARENT)
        setPadding(0, paddingTop, 0, paddingBottom)
        // Nothing typed here comes back after the app is killed and restored.
        isSaveEnabled = false
    }

    /** Newer Android starts credential autofill even for a view marked unimportant; no type, no offer. */
    override fun getAutofillType() = View.AUTOFILL_TYPE_NONE
}
