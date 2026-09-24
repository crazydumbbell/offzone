package com.exchip.offzone

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

/** Static Rue v6 portraits and full-body poses. Clothing variants are studies, not entitlements. */
enum class RueExpression(@DrawableRes val asset: Int) {
    WELCOME(R.drawable.rue_portraits_welcome), READY(R.drawable.rue_portraits_ready),
    FOCUSED(R.drawable.rue_portraits_focused), REFLECTION(R.drawable.rue_portraits_reflection),
    FINISHED(R.drawable.rue_portraits_finished), RECOVERED(R.drawable.rue_portraits_recovered),
    NEEDS_ACTION(R.drawable.rue_portraits_needs_action), FAILED(R.drawable.rue_portraits_failed),
    WELCOME_FULL(R.drawable.rue_fullbody_welcome), READY_FULL(R.drawable.rue_fullbody_ready),
    FOCUSED_FULL(R.drawable.rue_fullbody_focused), RECOVERED_FULL(R.drawable.rue_fullbody_recovered)
}

@Composable
fun RueView(expression: RueExpression = RueExpression.WELCOME, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(expression.asset),
        contentDescription = stringResource(R.string.rue_description),
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
