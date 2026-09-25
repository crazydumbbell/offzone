package com.exchip.offzone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/** The selected Nook Cat in states shared by onboarding and focus screens. */
enum class NookExpression(@DrawableRes val asset: Int) {
    WELCOME(R.drawable.nook_portraits_welcome), READY(R.drawable.nook_portraits_ready),
    FOCUSED(R.drawable.nook_portraits_focused), REFLECTION(R.drawable.nook_portraits_reflection),
    FINISHED(R.drawable.nook_portraits_finished), RECOVERED(R.drawable.nook_portraits_recovered),
    NEEDS_ACTION(R.drawable.nook_portraits_needs_action), FAILED(R.drawable.nook_portraits_failed),
    WELCOME_FULL(R.drawable.nook_fullbody_welcome), READY_FULL(R.drawable.nook_fullbody_ready),
    FOCUSED_FULL(R.drawable.nook_fullbody_focused), RECOVERED_FULL(R.drawable.nook_fullbody_recovered)
}

@Composable
fun NookCatView(expression: NookExpression = NookExpression.WELCOME, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(expression.asset),
        contentDescription = stringResource(R.string.nook_description),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
