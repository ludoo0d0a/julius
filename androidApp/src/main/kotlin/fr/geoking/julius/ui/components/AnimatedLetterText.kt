package fr.geoking.julius.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.geoking.julius.TextAnimation

@Composable
fun AnimatedLetterText(
    text: String,
    animation: TextAnimation,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.White,
    textAlign: TextAlign = TextAlign.Start
) {
    // We want to animate letters as they appear.
    // If the text grows, new letters should animate.
    // To handle this properly, we use a key for each letter's position to maintain its animation state.

    // For simplicity, let's split the text into words and then letters to allow wrapping
    val words = remember(text) { text.split(" ") }

    com.google.accompanist.flowlayout.FlowRow(
        modifier = modifier,
        mainAxisAlignment = when (textAlign) {
            TextAlign.Center -> com.google.accompanist.flowlayout.MainAxisAlignment.Center
            TextAlign.End -> com.google.accompanist.flowlayout.MainAxisAlignment.End
            else -> com.google.accompanist.flowlayout.MainAxisAlignment.Start
        }
    ) {
        words.forEachIndexed { wordIndex, word ->
            Row {
                word.forEachIndexed { charIndex, char ->
                    // Unique key for each letter instance in the text
                    key("${wordIndex}_${charIndex}_${char}") {
                        AnimatedLetter(
                            char = char,
                            animation = animation,
                            style = style,
                            color = color
                        )
                    }
                }
                // Add space after word if not the last one
                if (wordIndex < words.size - 1) {
                    Text(text = " ", style = style)
                }
            }
        }
    }
}

@Composable
fun AnimatedLetter(
    char: Char,
    animation: TextAnimation,
    style: TextStyle,
    color: Color
) {
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }

    val transition = updateTransition(transitionState, label = "LetterTransition")

    val alpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 500) },
        label = "Alpha"
    ) { state -> if (state) 1f else 0f }

    val blurRadius by transition.animateDp(
        transitionSpec = { tween(durationMillis = 500) },
        label = "Blur"
    ) { state -> if (state || animation != TextAnimation.Blur) 0.dp else 10.dp }

    val scale by transition.animateFloat(
        transitionSpec = {
            if (animation == TextAnimation.Genie) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            } else {
                tween(durationMillis = 500)
            }
        },
        label = "Scale"
    ) { state ->
        if (state) 1f
        else when (animation) {
            TextAnimation.Zoom -> 0f
            TextAnimation.Genie -> 0f
            else -> 1f
        }
    }

    val translationY by transition.animateFloat(
        transitionSpec = {
            if (animation == TextAnimation.Falling) {
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            } else {
                tween(durationMillis = 500)
            }
        },
        label = "TranslationY"
    ) { state ->
        if (state || animation != TextAnimation.Falling) 0f else -100f
    }

    val genieOffset by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 600, easing = FastOutSlowInEasing) },
        label = "GenieOffset"
    ) { state -> if (state || animation != TextAnimation.Genie) 0f else 200f }

    Text(
        text = char.toString(),
        style = style,
        color = color,
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
                this.translationY = translationY

                if (animation == TextAnimation.Genie) {
                    this.translationY = genieOffset
                    this.transformOrigin = TransformOrigin(0.5f, 1f)
                    // Simple genie-like shear/pinch could be added here if needed
                }
            }
            .then(if (animation == TextAnimation.Blur) Modifier.blur(blurRadius) else Modifier)
    )
}
