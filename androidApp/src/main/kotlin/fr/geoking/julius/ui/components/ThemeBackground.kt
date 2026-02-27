package fr.geoking.julius.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.AppSettings
import fr.geoking.julius.AppTheme
import fr.geoking.julius.shared.VoiceEvent
import fr.geoking.julius.ui.anim.AnimationPalette
import fr.geoking.julius.ui.anim.AnimationPalettes
import fr.geoking.julius.ui.anim.phone.FractalEffectCanvas
import fr.geoking.julius.ui.anim.phone.MicroEffectCanvas
import fr.geoking.julius.ui.anim.phone.ParticlesEffectCanvas
import fr.geoking.julius.ui.anim.phone.SphereEffectCanvas
import fr.geoking.julius.ui.anim.phone.WavesEffectCanvas

/**
 * Full-screen background effect based on selected theme; intensity follows isActive.
 */
@Composable
fun ThemeBackground(
    theme: AppTheme,
    status: VoiceEvent,
    palette: AnimationPalette,
    settings: AppSettings = AppSettings()
) {
    val isActive = status == VoiceEvent.Listening || status == VoiceEvent.Speaking
    when (theme) {
        AppTheme.Particles -> ParticlesEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Sphere -> SphereEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Waves -> WavesEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Fractal -> FractalEffectCanvas(
            isActive = isActive,
            palette = palette,
            quality = settings.fractalQuality,
            colorIntensity = settings.fractalColorIntensity
        )
        AppTheme.Micro -> MicroEffectCanvas(status = status, palette = palette)
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundParticlesIdlePreview() {
    ThemeBackground(
        theme = AppTheme.Particles,
        status = VoiceEvent.Silence,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundParticlesActivePreview() {
    ThemeBackground(
        theme = AppTheme.Particles,
        status = VoiceEvent.Listening,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundSpherePreview() {
    ThemeBackground(
        theme = AppTheme.Sphere,
        status = VoiceEvent.Listening,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundWavesPreview() {
    ThemeBackground(
        theme = AppTheme.Waves,
        status = VoiceEvent.Listening,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundFractalPreview() {
    ThemeBackground(
        theme = AppTheme.Fractal,
        status = VoiceEvent.Listening,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundMicroPreview() {
    ThemeBackground(
        theme = AppTheme.Micro,
        status = VoiceEvent.Listening,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex)
    )
}
