// Archivo de build raiz: solo declara el plugin de Android (Kotlin viene integrado en AGP 9).
plugins {
    alias(libs.plugins.android.application) apply false
}
