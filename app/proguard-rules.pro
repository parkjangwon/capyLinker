# Gson uses generic type information stored in a class's signature to determine
# the types for fields. If you are using generic types, you must keep the signature.
-keepattributes Signature

# For GSON annotations
-keepattributes *Annotation*

# Prevent R8 from warning about Gson's use of reflection.
-dontwarn com.google.gson.**

# Keep specific data classes and their members that are serialized by Gson/Room.
-keep class org.parkjw.capylinker.data.repository.AnalysisResult { *; }
-keep class org.parkjw.capylinker.data.database.LinkEntity { *; }

# Keep the Converters class and its members explicitly.
-keep class org.parkjw.capylinker.data.database.Converters { *; }

# Keep Room TypeConverter methods.
-keepclasseswithmembers public class * {
    @androidx.room.TypeConverter
    public <methods>;
}

# Keep classes from Google's AI SDK
-keep class com.google.ai.client.generativeai.type.** { *; }

# Keep Gson specific classes
-keep public class * extends com.google.gson.TypeAdapter
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
