# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.nearbycoder.thumbpotato.**$$serializer { *; }
-keepclassmembers class com.nearbycoder.thumbpotato.** { *** Companion; }
-keepclasseswithmembers class com.nearbycoder.thumbpotato.** { kotlinx.serialization.KSerializer serializer(...); }
