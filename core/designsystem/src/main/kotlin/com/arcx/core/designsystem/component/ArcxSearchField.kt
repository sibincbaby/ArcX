package com.arcx.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.arcx.core.designsystem.theme.Spacing

/**
 * The search box, for every list that has one.
 *
 * Four of these existed. Three were filled [TextField]s with transparent indicators; the runner's
 * picker was an [androidx.compose.material3.OutlinedTextField] with a 16dp corner instead of 14.
 * The filled treatment wins because it is both the majority and the reason the indicators were
 * dropped in the first place: a box without a rule under it reads as something to search with,
 * and one with a rule reads as a field on a form.
 *
 * The clear button is not optional. Home's copy was the only one without it, which meant the one
 * search a user reaches for most — the grid on the first screen — was also the only one they had
 * to hold backspace to escape.
 *
 * [placeholder] is a parameter rather than a constant because the library counts what it is
 * searching ("Search 16 workflows"), and that count answers "is it worth typing" before the
 * typing.
 */
@Composable
fun ArcxSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Md),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                // IconButton, not a bare clickable icon: it brings the 48dp target with it.
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        // Nothing here is a sentence, and the search key beats a newline in a single-line field.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search,
        ),
        // Dropping the indicator turns a form input into something that reads as a search box.
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}
