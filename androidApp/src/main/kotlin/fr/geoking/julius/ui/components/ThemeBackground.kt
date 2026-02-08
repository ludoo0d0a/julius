package fr.geoking.julius.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.geoking.julius.AppTheme

/**
 * Full-screen background effect based on selected theme; intensity follows isActive.
 */
@Composable
fun ThemeBackground(
    theme: AppTheme,
    isActive: Boolean
) {
    when (theme) {
        AppTheme.Particles -> ParticleEffectCanvas(isActive = isActive)
        AppTheme.Sphere -> SphereEffectCanvas(isActive = isActive)
        AppTheme.Waves -> WavesEffectCanvas(isActive = isActive)
        AppTheme.Fractal -> FractalEffectCanvas(isActive = isActive)
    }
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundParticlesIdlePreview() {
    ThemeBackground(theme = AppTheme.Particles, isActive = false)
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundParticlesActivePreview() {
    ThemeBackground(theme = AppTheme.Particles, isActive = true)
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundSpherePreview() {
    ThemeBackground(theme = AppTheme.Sphere, isActive = true)
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundWavesPreview() {
    ThemeBackground(theme = AppTheme.Waves, isActive = true)
}

@Preview(showBackground = true)
@Composable
private fun ThemeBackgroundFractalPreview() {
    ThemeBackground(theme = AppTheme.Fractal, isActive = true)
}
