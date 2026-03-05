# Add project specific ProGuard rules here.

# React Native
-keep class com.facebook.react.** { *; }
-keep class com.facebook.jni.** { *; }
-dontwarn com.facebook.react.**

# Hermes
-keep class com.facebook.hermes.unicode.** { *; }
-keep class com.facebook.jni.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# react-native-keychain (uses secure storage)
-keep class com.oblador.keychain.** { *; }

# Crypto / random
-dontwarn org.slf4j.**
-dontwarn java.awt.**
