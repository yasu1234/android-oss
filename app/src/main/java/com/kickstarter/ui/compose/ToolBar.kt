package com.kickstarter.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.kickstarter.R

@Preview(showBackground = true, backgroundColor = 0xFFF0EAE2)
@Composable
fun ToolBarPreview() {
    MaterialTheme {
        TopToolBar(
            right = { ToolbarIconButton(icon = ImageVector.vectorResource(id = R.drawable.icon__heart)) },
            middle = { ToolbarIconToggleButton(icon = ImageVector.vectorResource(id = R.drawable.icon__heart)) }
        )
    }
}

@Composable
fun TopToolBar(
    title: String? = null,
    leftIcon: ImageVector = Icons.Filled.ArrowBack,
    leftOnClickAction: () -> Unit = {},
    right: @Composable () -> Unit = {},
    middle: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            title?.let {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = { leftOnClickAction() }) {
                Icon(
                    imageVector = leftIcon,
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        },
        actions = {
            middle()
            right()
        },
        backgroundColor = colorResource(id = R.color.kds_white)
    )
}

@Composable
fun ToolbarIconButton(icon: ImageVector, clickAction: () -> Unit = {}) =
    IconButton(onClick = { clickAction.invoke() }) {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
    }

@Composable
fun ToolbarIconToggleButton(
    icon: ImageVector,
    checkedImageVector: ImageVector? = null,
    clickAction: () -> Unit = {},
    initialState: Boolean = false,
    initialStateTintColor: Color = kds_black,
    onCheckClicked: Color = kds_celebrate_700
) {
    val state = remember { mutableStateOf(initialState) }
    state.value = initialState
    return IconToggleButton(checked = state.value, onCheckedChange = {
        clickAction.invoke()
        state.value = it
    }) {
        val tint = animateColorAsState(if (state.value) onCheckClicked else initialStateTintColor)
        val imageVector = if (state.value) checkedImageVector ?: icon else icon

        Icon(imageVector = imageVector, contentDescription = null, tint = tint.value)
    }
}
