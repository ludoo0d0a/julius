package fr.geoking.julius.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.AppTheme
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
    isActive: Boolean,
    palette: AnimationPalette
) {
    when (theme) {
        AppTheme.Particles -> ParticlesEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Sphere -> SphereEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Waves -> WavesEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Fractal -> FractalEffectCanvas(isActive = isActive, palette = palette)
        AppTheme.Micro -> MicroEffectCanvas(isActive = isActive, palette = palette)
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundParticlesIdlePreview() {
    ThemeBackground(
        theme = AppTheme.Particles,
        isActive = false,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundParticlesActivePreview() {
    ThemeBackground(
        theme = AppTheme.Particles,
        isActive = true,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundSpherePreview() {
    ThemeBackground(
        theme = AppTheme.Sphere,
        isActive = true,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundWavesPreview() {
    ThemeBackground(
        theme = AppTheme.Waves,
        isActive = true,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundFractalPreview() {
    ThemeBackground(
        theme = AppTheme.Fractal,
        isActive = true,
        palette = AnimationPalettes.paletteFor(0)
    )
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundMicroPreview() {
    ThemeBackground(
        theme = AppTheme.Micro,
        isActive = true,
        palette = AnimationPalettes.paletteFor(AnimationPalettes.microPaletteIndex)
    )
}
